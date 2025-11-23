# services/training_service.py
import os

import tensorflow as tf
from tensorflow import keras
import numpy as np
import time
import logging
import requests
import json

from config import Config
from models_code.model_factory import build_model_from_config
from tensorflow.keras.preprocessing.image import ImageDataGenerator

logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

# 给这个 logger 单独加一个控制台输出（避免被 root 的 WARNING 级别过滤）
if not logger.handlers:
    console_handler = logging.StreamHandler()
    console_handler.setLevel(logging.INFO)
    formatter = logging.Formatter(
        '%(asctime)s [%(levelname)s] %(name)s - %(message)s'
    )
    console_handler.setFormatter(formatter)
    logger.addHandler(console_handler)
    logger.propagate = False  # 不再往 root 传，避免重复输出

class TrainingService:
    def __init__(self, task_id, springboot_url='http://localhost:8080'):
        self.task_id = task_id
        self.springboot_url = springboot_url
        self.is_cancelled = False

    def start_training(self, training_config_str, dataset_config_str):
        """启动训练"""
        try:
            # 解析配置（兼容字符串 / dict）
            training_config = (
                json.loads(training_config_str)
                if isinstance(training_config_str, str)
                else (training_config_str or {})
            )
            dataset_config = (
                json.loads(dataset_config_str)
                if isinstance(dataset_config_str, str)
                else (dataset_config_str or {})
            )

            logger.info(f"[task {self.task_id}] training_config = {training_config}")
            logger.info(f"[task {self.task_id}] dataset_config = {dataset_config}")

            # 小工具：把各种 true/"true"/"1" 统一成 bool
            def _to_bool(v, default=False):
                if v is None:
                    return default
                if isinstance(v, bool):
                    return v
                if isinstance(v, (int, float)):
                    return v != 0
                if isinstance(v, str):
                    return v.strip().lower() in ("1", "true", "t", "yes", "y")
                return bool(v)

            # -------- 解析基础超参数（epochs / batch_size），兼容新旧字段 --------
            hp_cfg = training_config.get("hyperparameters") or {}

            # epochs
            epochs = (
                    hp_cfg.get("epochs")
                    or training_config.get("epochs")
                    or training_config.get("totalEpochs")
                    or Config.DEFAULT_EPOCHS
            )
            # batch size
            batch_size = (
                    hp_cfg.get("batchSize")
                    or hp_cfg.get("batch_size")
                    or training_config.get("batchsize")
                    or training_config.get("batchSize")
                    or Config.DEFAULT_BATCH_SIZE
            )

            epochs = int(epochs)
            batch_size = int(batch_size)

            # -------- 数据集选择（支持 mnist / fashion_mnist） --------
            dataset_cfg = training_config.get("dataset") or {}
            dataset_name = (
                    dataset_cfg.get("name")
                    or dataset_cfg.get("datasetName")
                    or dataset_config.get("datasetName")
                    or Config.DEFAULT_DATASET
            )

            # 数据增强开关（优先用 training_config.dataset，再兼容 dataset_config）
            use_augmentation = _to_bool(
                dataset_cfg.get("useAugmentation")
                or dataset_cfg.get("useaugmentation")
                or dataset_config.get("useAugmentation")
                or dataset_config.get("useaugmentation")
                or hp_cfg.get("useAugmentation")
                or hp_cfg.get("useaugmentation")
                or training_config.get("useAugmentation")
                or training_config.get("useaugmentation")
                or False
            )

            logger.info(
                f"[task {self.task_id}] 开始训练 - dataset={dataset_name}, "
                f"epochs={epochs}, batch_size={batch_size},"
                f"use_augmentation={use_augmentation}"
            )

            (x_train, y_train), (x_test, y_test) = self._load_dataset(dataset_name)

            # 数据预处理：reshape -> 28x28x1, 归一化 + one-hot
            x_train = x_train.reshape(x_train.shape[0], 28, 28, 1).astype('float32') / 255.0
            x_test = x_test.reshape(x_test.shape[0], 28, 28, 1).astype('float32') / 255.0
            y_train = keras.utils.to_categorical(y_train, 10)
            y_test = keras.utils.to_categorical(y_test, 10)

            # -------- 构建模型（多架构 + 超参数） --------
            model = build_model_from_config(training_config)

            # -------- 训练回调：进度 + EarlyStopping + LR 调度 --------
            callbacks = [self._build_progress_callback()]

            # 早停配置
            use_early_stopping = bool(
                hp_cfg.get("useEarlyStopping")
                if hp_cfg.get("useEarlyStopping") is not None
                else training_config.get("useEarlyStopping", True)
            )
            early_cfg = hp_cfg.get("earlyStopping") if isinstance(hp_cfg.get("earlyStopping"), dict) else {}
            if use_early_stopping:
                monitor = early_cfg.get("monitor", "val_accuracy")
                patience = int(early_cfg.get("patience", Config.EARLY_STOPPING_PATIENCE))
                callbacks.append(
                    keras.callbacks.EarlyStopping(
                        monitor=monitor,
                        patience=patience,
                        restore_best_weights=True,
                        verbose=1
                    )
                )

            # 学习率调度
            use_lr_scheduler = bool(
                hp_cfg.get("useLRScheduler")
                or training_config.get("useLRScheduler")
                or False
            )
            if use_lr_scheduler:
                callbacks.append(
                    keras.callbacks.ReduceLROnPlateau(
                        monitor='val_loss',
                        factor=0.5,
                        patience=2,
                        verbose=1
                    )
                )

            # -------- 开始训练 --------
            if use_augmentation:
                logger.info(f"[task {self.task_id}] 使用数据增强训练")
                datagen = ImageDataGenerator(
                    rotation_range=10,
                    width_shift_range=0.1,
                    height_shift_range=0.1,
                    zoom_range=0.1
                )
                datagen.fit(x_train)
                train_data = datagen.flow(x_train, y_train, batch_size=batch_size)

                history = model.fit(
                    train_data,
                    epochs=epochs,
                    validation_data=(x_test, y_test),
                    callbacks=callbacks,
                    verbose=1
                )
            else:
                history = model.fit(
                    x_train, y_train,
                    batch_size=batch_size,
                    epochs=epochs,
                    validation_data=(x_test, y_test),
                    callbacks=callbacks,
                    verbose=1
                )

            if not self.is_cancelled:
                # 保存模型文件到 Config.MODEL_PATH 目录
                model_dir = Config.MODEL_PATH
                model_dir.mkdir(parents=True, exist_ok=True)
                model_filename = f"trained_model_{self.task_id}_{int(time.time())}.h5"
                model_path = model_dir / model_filename
                model.save(model_path)

                # 在测试集上评估
                test_loss, test_accuracy = model.evaluate(x_test, y_test, verbose=0)
                model_size = os.path.getsize(model_path)

                # 报告完成（字段保持兼容）
                result_data = {
                    'finalAccuracy': float(test_accuracy),
                    'finalLoss': float(test_loss),
                    'modelPath': str(model_path),
                    'trainingSamples': int(len(x_train)),
                    'testSamples': int(len(x_test)),
                    'modelSize': int(model_size)
                    # 这里以后可以加 'trainingConfig': training_config 给 SpringBoot 入库
                }

                self.report_completion(result_data)
                logger.info(f"训练任务 {self.task_id} 完成，准确率: {test_accuracy:.4f}")

        except Exception as e:
            logger.error(f"训练任务 {self.task_id} 失败: {e}", exc_info=True)
            self.report_failure(str(e))

    # ------------------------------------------------------------------

    def _load_dataset(self, name):
        """根据名称加载数据集（目前支持 mnist / fashion_mnist）"""
        name = str(name).lower()
        if name == 'mnist':
            return keras.datasets.mnist.load_data()
        elif name in ('fashion_mnist', 'fashion-mnist', 'fashion'):
            from tensorflow.keras.datasets import fashion_mnist
            return fashion_mnist.load_data()
        else:
            # 以后你可以在这里扩展自定义数据集逻辑
            raise ValueError(f"Unsupported dataset: {name}")

    def _build_progress_callback(self):
        """构建一个 Keras Callback，用于每个 epoch 结束时向 SpringBoot 报告进度"""

        service = self

        class ProgressCallback(keras.callbacks.Callback):
            def __init__(self, service):
                super().__init__()
                self.service = service

            def on_epoch_end(self, epoch, logs=None):
                logs = logs or {}
                if self.service.is_cancelled:
                    self.model.stop_training = True
                    return

                total_epochs = self.params.get('epochs', 1)
                progress = ((epoch + 1) / total_epochs) * 100

                progress_data = {
                    'currentEpoch': int(epoch + 1),
                    'progress': float(progress),
                    'loss': float(logs.get('loss', 0.0)),
                    'accuracy': float(logs.get('accuracy', 0.0)),
                    'valLoss': float(logs.get('val_loss', 0.0)),
                    'valAccuracy': float(logs.get('val_accuracy', 0.0))
                }

                self.service.report_progress(progress_data)

        return ProgressCallback(service)

    # ------------------------------------------------------------------
    # 下面三个方法保持原样（向 SpringBoot 回调）
    # ------------------------------------------------------------------

    def report_progress(self, progress_data):
        """向SpringBoot报告训练进度"""
        try:
            url = f"{self.springboot_url}/api/training/tasks/{self.task_id}/progress"
            logger.warning(url)
            headers = {'Content-Type': 'application/json'}
            response = requests.post(url, json=progress_data, headers=headers, timeout=5)

            if response.status_code != 200:
                logger.warning(f"报告进度失败: {response.status_code}")
        except Exception as e:
            logger.error(f"报告进度异常: {e}")

    def report_completion(self, result_data):
        """向SpringBoot报告训练完成"""
        try:
            url = f"{self.springboot_url}/api/training/tasks/{self.task_id}/complete"
            headers = {'Content-Type': 'application/json'}
            response = requests.post(url, json=result_data, headers=headers, timeout=5)

            if response.status_code != 200:
                logger.warning(f"报告完成失败: {response.status_code}")
        except Exception as e:
            logger.error(f"报告完成异常: {e}")

    def report_failure(self, error_message):
        """向SpringBoot报告训练失败"""
        try:
            url = f"{self.springboot_url}/api/training/tasks/{self.task_id}/fail"
            headers = {'Content-Type': 'application/json'}
            data = {'errorMessage': error_message}
            response = requests.post(url, json=data, headers=headers, timeout=5)

            if response.status_code != 200:
                logger.warning(f"报告失败状态失败: {response.status_code}")
        except Exception as e:
            logger.error(f"报告失败状态异常: {e}")
