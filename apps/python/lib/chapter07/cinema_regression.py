from dataclasses import dataclass, replace
from pathlib import Path

import numpy as np
import pandas as pd

from lib.chapter02.iris_preprocessing import (
    TrainTestSplit,
    column_means,
    fill_missing,
    split_train_test,
)

FEATURES = ["SNS1", "SNS2", "actor", "original"]
TARGET = "sales"


@dataclass(frozen=True)
class LinearModel:
    intercept: float
    coefficients: dict[str, float]

    def predict(self, x: pd.DataFrame) -> np.ndarray:
        features = x[list(self.coefficients)].to_numpy(dtype=float)
        weights = np.array(list(self.coefficients.values()))
        return np.asarray(self.intercept + features @ weights, dtype=float)


def load_cinema(csv_file: Path) -> pd.DataFrame:
    return pd.read_csv(csv_file)


def remove_outliers(df: pd.DataFrame) -> pd.DataFrame:
    is_outlier = (df["SNS2"] > 1000) & (df["sales"] < 8500)
    return df[~is_outlier]


def prepare_cinema(csv_file: Path, test_size: float, seed: int) -> TrainTestSplit:
    df = remove_outliers(load_cinema(csv_file))
    split = split_train_test(df[FEATURES], df[TARGET], test_size=test_size, seed=seed)
    means = column_means(split.x_train, FEATURES)
    return replace(
        split,
        x_train=fill_missing(split.x_train, means),
        x_test=fill_missing(split.x_test, means),
    )


def fit_linear_regression(x: pd.DataFrame, t: pd.Series) -> LinearModel:
    design = np.column_stack([np.ones(len(x)), x.to_numpy(dtype=float)])
    target = t.to_numpy(dtype=float)
    weights = np.linalg.solve(design.T @ design, design.T @ target)
    return LinearModel(
        intercept=float(weights[0]),
        coefficients={
            column: float(w) for column, w in zip(x.columns, weights[1:], strict=True)
        },
    )


def mean_absolute_error(t: pd.Series, y: np.ndarray) -> float:
    return float(np.mean(np.abs(t.to_numpy(dtype=float) - y)))


def root_mean_squared_error(t: pd.Series, y: np.ndarray) -> float:
    return float(np.sqrt(np.mean((t.to_numpy(dtype=float) - y) ** 2)))


def r2_score(t: pd.Series, y: np.ndarray) -> float:
    actual = t.to_numpy(dtype=float)
    residual = np.sum((actual - y) ** 2)
    total = np.sum((actual - actual.mean()) ** 2)
    return float(1 - residual / total)
