# models_code/model_factory.py - 根据配置构建不同模型结构
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

from config import Config
from .cnn_model import (
    create_cnn_model,
    create_advanced_cnn_model,
    create_deep_cnn_model,
    create_resnet_like_model,
    compile_model,
)


def _normalize_model_type(raw_type: str) -> str:
    """把各种写法统一成内部的几种类型"""
    if not raw_type:
        return "cnn_basic"

    t = str(raw_type).lower()

    if t in ("cnn", "cnn_basic", "simple", "basic"):
        return "cnn_basic"

    if t in ("cnn_adv", "cnn_advanced", "advanced"):
        return "cnn_advanced"

    if t in ("deep", "deep_cnn", "cnn_deep"):
        return "cnn_deep"

    if t in ("resnet", "resnet_like", "resnet-like"):
        return "cnn_resnet"

    if t in ("mlp", "dense", "fc"):
        return "mlp"

    return "cnn_basic"


def _get_from_config(config: dict, hp: dict, keys, default=None, cast=None):
    """
    从 hyperparameters 或顶层 config 里拿值
    keys: 可以是单个 key 或 key 列表（兼容大小写/旧字段名）
    """
    if isinstance(keys, str):
        keys = [keys]

    value = None
    for k in keys:
        if k in hp and hp[k] is not None:
            value = hp[k]
            break
        if k in config and config[k] is not None:
            value = config[k]
            break

    if value is None:
        value = default

    if cast is not None and value is not None:
        try:
            value = cast(value)
        except (TypeError, ValueError):
            value = default

    return value


def build_model_from_config(training_config: dict) -> keras.Model:
    """
    根据 training_config 构建并编译一个模型。

    支持字段（兼容大小写/老写法）：
    - modelType / modeltype: cnn_basic / cnn_advanced / mlp
    - learningRate / learningrate
    - optimizer
    - lossFunction / lossfunction
    - hiddenSize / hiddensize（MLP 的隐藏层大小）
    - activation（MLP 激活函数）
    - dropout（MLP dropout）
    """
    if training_config is None:
        training_config = {}

    hp_cfg = training_config.get("hyperparameters") or {}

    # ---- 解析通用超参数 ----
    learning_rate = _get_from_config(
        training_config, hp_cfg,
        keys=["learningRate", "learningrate"],
        default=Config.DEFAULT_LEARNING_RATE,
        cast=float
    )

    optimizer_name = _get_from_config(
        training_config, hp_cfg,
        keys=["optimizer"],
        default="adam",
        cast=str
    )

    loss_fn = _get_from_config(
        training_config, hp_cfg,
        keys=["loss", "lossFunction", "lossfunction"],
        default="categorical_crossentropy",
        cast=str
    )

    raw_model_type = _get_from_config(
        training_config, hp_cfg,
        keys=["modelType", "modeltype"],
        default="cnn_basic",
        cast=str
    )
    model_type = _normalize_model_type(raw_model_type)

    # 统一输入输出规格：跟 training_service 里预处理一致
    input_shape = (28, 28, 1)
    num_classes = 10

    # ---- 按类型构建模型结构 ----
    if model_type == "cnn_basic":
        model = create_cnn_model(input_shape=input_shape, num_classes=num_classes)

    elif model_type == "cnn_advanced":
        model = create_advanced_cnn_model(input_shape=input_shape, num_classes=num_classes)

    elif model_type == "cnn_deep":
        model = create_deep_cnn_model(input_shape=input_shape, num_classes=num_classes)

    elif model_type == "cnn_resnet":
        model = create_resnet_like_model(input_shape=input_shape, num_classes=num_classes)

    elif model_type == "mlp":
        # 全连接网络（MLP），受 hiddenSize / dropout / activation 控制
        hidden_size = _get_from_config(
            training_config, hp_cfg,
            keys=["hiddenSize", "hiddensize"],
            default=128,
            cast=int
        )
        activation = _get_from_config(
            training_config, hp_cfg,
            keys=["activation"],
            default="relu",
            cast=str
        )
        dropout_rate = _get_from_config(
            training_config, hp_cfg,
            keys=["dropout"],
            default=0.5,
            cast=float
        )

        model = keras.Sequential([
            layers.Input(shape=input_shape),
            layers.Flatten(),
            layers.Dense(hidden_size, activation=activation),
            layers.Dropout(dropout_rate),
            layers.Dense(hidden_size, activation=activation),
            layers.Dropout(dropout_rate),
            layers.Dense(num_classes, activation="softmax"),
        ])

    else:
        # 未知类型就退回 basic CNN，确保不会训练挂掉
        model = create_cnn_model(input_shape=input_shape, num_classes=num_classes)

    # ---- 统一编译模型 ----
    model = compile_model(
        model,
        optimizer_name=optimizer_name,
        learning_rate=learning_rate,
        loss=loss_fn,
        metrics=["accuracy"]
    )

    return model