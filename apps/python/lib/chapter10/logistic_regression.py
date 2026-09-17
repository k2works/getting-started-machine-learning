import numpy as np
import pandas as pd
from numpy.typing import NDArray

EPSILON = 1e-12


def softmax(z: NDArray[np.float64]) -> NDArray[np.float64]:
    shifted = z - z.max(axis=1, keepdims=True)
    exp = np.exp(shifted)
    return exp / exp.sum(axis=1, keepdims=True)


def cross_entropy(
    probabilities: NDArray[np.float64], targets: NDArray[np.float64]
) -> float:
    return float(-np.mean(np.sum(targets * np.log(probabilities + EPSILON), axis=1)))


class LogisticRegression:
    def __init__(self, learning_rate: float = 1.0, epochs: int = 5000) -> None:
        self.learning_rate = learning_rate
        self.epochs = epochs
        self.classes: list[str] = []
        self.weights = np.zeros((0, 0))
        self.bias = np.zeros(0)
        self.losses: list[float] = []

    def fit(self, x: pd.DataFrame, t: pd.Series) -> "LogisticRegression":
        features = x.to_numpy(dtype=float)
        self.classes = sorted({str(label) for label in t})
        labels = t.astype(str).to_list()
        targets = np.eye(len(self.classes))[[self.classes.index(v) for v in labels]]
        self.weights = np.zeros((features.shape[1], len(self.classes)))
        self.bias = np.zeros(len(self.classes))
        self.losses = []
        for _ in range(self.epochs):
            probabilities = softmax(features @ self.weights + self.bias)
            self.losses.append(cross_entropy(probabilities, targets))
            gradient = (probabilities - targets) / len(features)
            self.weights -= self.learning_rate * features.T @ gradient
            self.bias -= self.learning_rate * gradient.sum(axis=0)
        return self

    def predict(self, x: pd.DataFrame) -> list[str]:
        if not self.classes:
            raise ValueError("fit で学習してから predict を呼んでください")
        scores = x.to_numpy(dtype=float) @ self.weights + self.bias
        return [self.classes[i] for i in scores.argmax(axis=1)]
