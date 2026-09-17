from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd
from numpy.typing import NDArray
from sklearn.preprocessing import PolynomialFeatures

from lib.chapter02.iris_preprocessing import split_train_test

FEATURES = ["RM", "PTRATIO", "LSTAT"]
TARGET = "PRICE"
OUTLIER_THRESHOLD = 3.0


@dataclass(frozen=True)
class PolynomialScaler:
    mean: pd.Series
    std: pd.Series
    polynomial: PolynomialFeatures


@dataclass(frozen=True)
class BostonDataset:
    x_train: NDArray[np.float64]
    t_train: NDArray[np.float64]
    x_valid: NDArray[np.float64]
    t_valid: NDArray[np.float64]
    x_test: NDArray[np.float64]
    t_test: NDArray[np.float64]
    feature_names: list[str]


def remove_outliers(
    df: pd.DataFrame, columns: list[str], threshold: float
) -> pd.DataFrame:
    values = df[columns]
    z = (values - values.mean()) / values.std()
    return df[~(z.abs() > threshold).any(axis=1)]


def fit_polynomial_scaler(x: pd.DataFrame) -> PolynomialScaler:
    mean = x.mean()
    std = x.std(ddof=0)
    polynomial = PolynomialFeatures(degree=2, include_bias=False)
    polynomial.fit((x - mean) / std)
    return PolynomialScaler(mean=mean, std=std, polynomial=polynomial)


def feature_names(scaler: PolynomialScaler) -> list[str]:
    return [str(name) for name in scaler.polynomial.get_feature_names_out()]


def transform(scaler: PolynomialScaler, x: pd.DataFrame) -> NDArray[np.float64]:
    standardized = (x - scaler.mean) / scaler.std
    return np.asarray(scaler.polynomial.transform(standardized), dtype=np.float64)


def prepare_boston(
    csv_file: Path, test_size: float, validation_size: float, seed: int
) -> BostonDataset:
    df = remove_outliers(pd.read_csv(csv_file), FEATURES + [TARGET], OUTLIER_THRESHOLD)
    outer = split_train_test(df[FEATURES], df[TARGET], test_size=test_size, seed=seed)
    inner = split_train_test(
        outer.x_train, outer.t_train, test_size=validation_size, seed=seed
    )
    scaler = fit_polynomial_scaler(inner.x_train)
    return BostonDataset(
        x_train=transform(scaler, inner.x_train),
        t_train=inner.t_train.to_numpy(dtype=np.float64),
        x_valid=transform(scaler, inner.x_test),
        t_valid=inner.t_test.to_numpy(dtype=np.float64),
        x_test=transform(scaler, outer.x_test),
        t_test=outer.t_test.to_numpy(dtype=np.float64),
        feature_names=feature_names(scaler),
    )
