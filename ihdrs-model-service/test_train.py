import requests

# 你的 Flask 训练接口地址
URL = "http://127.0.0.1:5000/api/train"

payload = {
    "taskId": 1,
    "taskName": "local-test",
    "trainingConfig": {
        "modelType": "mlp",
        "network": {
            # 这里就是你要选的模型架构：
            # simple_cnn / advanced_cnn / deep_cnn / resnet_like / mlp
            "arch": "mlp",
            "input_shape": [28, 28, 1],
            "num_classes": 10
        },
        "hyperparameters": {
            "epochs": 5,               # 先跑 2 轮，快一点
            "batchSize": 64,
            "learningRate": 0.01,
            "optimizer": "adam",
            "lossFunction": "categorical_crossentropy",
            "useEarlyStopping": True,
            "useLRScheduler": False
        },
        "dataset": {
            # 目前你的代码里是 mnist / fashion_mnist
            "name": "mnist"
        }
    },
    # 现在暂时不用，给个空对象就好
    "datasetConfig": {}
}

if __name__ == "__main__":
    resp = requests.post(URL, json=payload)
    print("status_code:", resp.status_code)
    print("response:", resp.text)
