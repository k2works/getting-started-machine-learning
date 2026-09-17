import math
from dataclasses import dataclass, replace
from pathlib import Path

import numpy as np
import pandas as pd

TARGET = "種類"


@dataclass(frozen=True)
class TrainTestSplit:
    x_train: pd.DataFrame
    x_test: pd.DataFrame
    t_train: pd.Series
    t_test: pd.Series


def load_iris(csv_file: Path) -> pd.DataFrame:
    return pd.read_csv(csv_file)


def count_missing(df: pd.DataFrame) -> pd.Series:
    return df.isna().sum()


def column_means(df: pd.DataFrame, columns: list[str]) -> dict[str, float]:
    return {column: float(df[column].mean()) for column in columns}


def fill_missing(df: pd.DataFrame, values: dict[str, float]) -> pd.DataFrame:
    return df.fillna(values)


def split_features_and_target(
    df: pd.DataFrame, target: str
) -> tuple[pd.DataFrame, pd.Series]:
    return df.drop(columns=[target]), df[target]


def split_train_test(
    x: pd.DataFrame, t: pd.Series, test_size: float, seed: int
) -> TrainTestSplit:
    positions = np.random.default_rng(seed).permutation(len(x))
    n_train = len(x) - math.ceil(len(x) * test_size)
    train, test = positions[:n_train], positions[n_train:]
    return TrainTestSplit(
        x_train=x.iloc[train],
        x_test=x.iloc[test],
        t_train=t.iloc[train],
        t_test=t.iloc[test],
    )


def prepare_iris(csv_file: Path, test_size: float, seed: int) -> TrainTestSplit:
    x, t = split_features_and_target(load_iris(csv_file), TARGET)
    split = split_train_test(x, t, test_size=test_size, seed=seed)
    means = column_means(split.x_train, list(x.columns))
    return replace(
        split,
        x_train=fill_missing(split.x_train, means),
        x_test=fill_missing(split.x_test, means),
    )
