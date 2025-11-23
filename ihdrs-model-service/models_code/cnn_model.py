# models_code/cnn_model.py - CNN模型定义
import tensorflow as tf
from tensorflow import keras
from tensorflow.keras import layers

def create_cnn_model(input_shape=(28, 28, 1), num_classes=10):
    """
    Args:
        input_shape: 输入图像形状
        num_classes: 分类数量（0-9数字）

    Returns:
        编译好的Keras模型
    """
    model = keras.Sequential([
        # 第一个卷积层
        layers.Conv2D(32, (3, 3), activation='relu', input_shape=input_shape),
        layers.MaxPooling2D((2, 2)),

        # 第二个卷积层
        layers.Conv2D(64, (3, 3), activation='relu'),
        layers.MaxPooling2D((2, 2)),

        # 第三个卷积层
        layers.Conv2D(64, (3, 3), activation='relu'),

        # 展平层
        layers.Flatten(),

        # 全连接层
        layers.Dense(64, activation='relu'),
        layers.Dropout(0.5),

        # 输出层
        layers.Dense(num_classes, activation='softmax')
    ])

    return model

def create_advanced_cnn_model(input_shape=(28, 28, 1), num_classes=10):
    """
    创建更复杂的CNN模型
    """
    model = keras.Sequential([
        # 第一个卷积块
        layers.Conv2D(32, (3, 3), activation='relu', input_shape=input_shape),
        layers.BatchNormalization(),
        layers.Conv2D(32, (3, 3), activation='relu'),
        layers.MaxPooling2D((2, 2)),
        layers.Dropout(0.25),

        # 第二个卷积块
        layers.Conv2D(64, (3, 3), activation='relu'),
        layers.BatchNormalization(),
        layers.Conv2D(64, (3, 3), activation='relu'),
        layers.MaxPooling2D((2, 2)),
        layers.Dropout(0.25),

        # 全连接层
        layers.Flatten(),
        layers.Dense(512, activation='relu'),
        layers.BatchNormalization(),
        layers.Dropout(0.5),
        layers.Dense(num_classes, activation='softmax')
    ])

    return model

def create_deep_cnn_model(input_shape=(28, 28, 1), num_classes=10):
    """更深的 CNN 模型"""
    inputs = keras.Input(shape=input_shape)

    x = layers.Conv2D(32, (3, 3), padding='same', activation='relu')(inputs)
    x = layers.BatchNormalization()(x)
    x = layers.Conv2D(32, (3, 3), padding='same', activation='relu')(x)
    x = layers.MaxPooling2D((2, 2))(x)
    x = layers.Dropout(0.25)(x)

    x = layers.Conv2D(64, (3, 3), padding='same', activation='relu')(x)
    x = layers.BatchNormalization()(x)
    x = layers.Conv2D(64, (3, 3), padding='same', activation='relu')(x)
    x = layers.MaxPooling2D((2, 2))(x)
    x = layers.Dropout(0.25)(x)

    x = layers.Conv2D(128, (3, 3), padding='same', activation='relu')(x)
    x = layers.BatchNormalization()(x)
    x = layers.MaxPooling2D((2, 2))(x)
    x = layers.Dropout(0.4)(x)

    x = layers.Flatten()(x)
    x = layers.Dense(256, activation='relu')(x)
    x = layers.BatchNormalization()(x)
    x = layers.Dropout(0.5)(x)
    outputs = layers.Dense(num_classes, activation='softmax')(x)

    model = keras.Model(inputs=inputs, outputs=outputs, name="deep_cnn")
    return model

def _residual_block(x, filters):
    """简单残差块，用于 ResNet-like 模型"""
    shortcut = x

    # 第一个卷积
    y = layers.Conv2D(filters, (3, 3), padding='same', activation='relu')(x)
    y = layers.BatchNormalization()(y)
    # 第二个卷积（不加激活，后面统一激活）
    y = layers.Conv2D(filters, (3, 3), padding='same')(y)
    y = layers.BatchNormalization()(y)

    # 如果通道数不一致，用 1x1 调整 shortcut 的通道
    if shortcut.shape[-1] != filters:
        shortcut = layers.Conv2D(filters, (1, 1), padding='same')(shortcut)
        shortcut = layers.BatchNormalization()(shortcut)

    out = layers.Add()([shortcut, y])
    out = layers.Activation('relu')(out)
    return out

def create_resnet_like_model(input_shape=(28, 28, 1), num_classes=10):
    """简化版 ResNet 结构"""
    inputs = keras.Input(shape=input_shape)

    x = layers.Conv2D(32, (3, 3), padding='same', activation='relu')(inputs)
    x = layers.BatchNormalization()(x)
    x = layers.MaxPooling2D((2, 2))(x)

    # 残差块 1
    x = _residual_block(x, 32)

    x = layers.MaxPooling2D((2, 2))(x)

    # 残差块 2
    x = _residual_block(x, 64)

    x = layers.GlobalAveragePooling2D()(x)
    x = layers.Dense(128, activation='relu')(x)
    x = layers.Dropout(0.5)(x)
    outputs = layers.Dense(num_classes, activation='softmax')(x)

    model = keras.Model(inputs=inputs, outputs=outputs, name="resnet_like")
    return model

def create_mlp_model(input_shape=(28, 28, 1), num_classes=10):
    """纯全连接 MLP 模型（不用卷积）"""
    inputs = keras.Input(shape=input_shape)
    x = layers.Flatten()(inputs)
    x = layers.Dense(512, activation='relu')(x)
    x = layers.Dropout(0.2)(x)
    x = layers.Dense(256, activation='relu')(x)
    x = layers.Dropout(0.2)(x)
    x = layers.Dense(128, activation='relu')(x)
    outputs = layers.Dense(num_classes, activation='softmax')(x)

    model = keras.Model(inputs=inputs, outputs=outputs, name="mlp")
    return model

def compile_model(model, optimizer_name='adam', learning_rate=0.001,
                  loss='categorical_crossentropy', metrics=None):
    """统一的模型编译函数，方便通过配置控制"""
    if metrics is None:
        metrics = ['accuracy']

    if optimizer_name.lower() == 'adam':
        optimizer = keras.optimizers.Adam(learning_rate=learning_rate)
    elif optimizer_name.lower() == 'sgd':
        optimizer = keras.optimizers.SGD(learning_rate=learning_rate, momentum=0.9)
    else:
        # 默认回退到 Adam
        optimizer = keras.optimizers.Adam(learning_rate=learning_rate)

    model.compile(optimizer=optimizer, loss=loss, metrics=metrics)
    return model