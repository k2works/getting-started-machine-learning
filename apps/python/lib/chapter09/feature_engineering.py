from dataclasses import dataclass, replace
from itertools import combinations_with_replacement
from pathlib import Path

import pandas as pd
from sklearn.linear_model import LinearRegression

from lib.chapter02.iris_preprocessing import (
    TrainTestSplit,
    column_means,
    fill_missing,
    split_features_and_target,
    split_train_test,
)

TARGET = "PRICE"


def dummy_categories(values: pd.Series) -> list[str]:
    return sorted(values.dropna().unique())[1:]


def encode_dummies(
    df: pd.DataFrame, column: str, categories: list[str]
) -> pd.DataFrame:
    dummies = {
        f"{column}_{category}": (df[column] == category).astype(int)
        for category in categories
    }
    return df.drop(columns=[column]).assign(**dummies)


@dataclass(frozen=True)
class Standardizer:
    means: dict[str, float]
    stds: dict[str, float]

    @classmethod
    def fit(cls, df: pd.DataFrame) -> "Standardizer":
        return cls(
            means={column: float(df[column].mean()) for column in df.columns},
            stds={
                column: float(df[column].std(ddof=0)) or 1.0 for column in df.columns
            },
        )

    def transform(self, df: pd.DataFrame) -> pd.DataFrame:
        return df.assign(
            **{
                column: (df[column] - self.means[column]) / self.stds[column]
                for column in self.means
            }
        )


def polynomial_features(df: pd.DataFrame, columns: list[str]) -> pd.DataFrame:
    products = {
        term_name(left, right): df[left] * df[right]
        for left, right in combinations_with_replacement(columns, 2)
    }
    return df[columns].assign(**products)


def term_name(left: str, right: str) -> str:
    return f"{left}^2" if left == right else f"{left} {right}"


def iqr_outliers(values: pd.Series, k: float = 1.5) -> pd.Series:
    q1, q3 = values.quantile(0.25), values.quantile(0.75)
    iqr = q3 - q1
    return (values < q1 - k * iqr) | (values > q3 + k * iqr)


def prepare_boston(csv_file: Path, test_size: float, seed: int) -> TrainTestSplit:
    df = pd.read_csv(csv_file)
    encoded = encode_dummies(df, "CRIME", dummy_categories(df["CRIME"]))
    x, t = split_features_and_target(encoded, TARGET)
    split = split_train_test(x, t, test_size=test_size, seed=seed)
    means = column_means(split.x_train, list(x.columns))
    return replace(
        split,
        x_train=fill_missing(split.x_train, means),
        x_test=fill_missing(split.x_test, means),
    )


def remove_target_outliers(split: TrainTestSplit) -> TrainTestSplit:
    keep = ~iqr_outliers(split.t_train)
    return replace(split, x_train=split.x_train[keep], t_train=split.t_train[keep])


def score_feature_set(
    split: TrainTestSplit, columns: list[str], terms: list[str]
) -> tuple[float, float]:
    x_train = polynomial_features(split.x_train, columns)[terms]
    x_test = polynomial_features(split.x_test, columns)[terms]
    standardizer = Standardizer.fit(x_train)
    x_train, x_test = standardizer.transform(x_train), standardizer.transform(x_test)
    model = LinearRegression().fit(x_train, split.t_train)
    return (
        float(model.score(x_train, split.t_train)),
        float(model.score(x_test, split.t_test)),
    )


def load_bike(tsv_file: Path) -> pd.DataFrame:
    return pd.read_csv(tsv_file, sep="\t")


def load_weather(csv_file: Path) -> pd.DataFrame:
    return pd.read_csv(csv_file, encoding="shift_jis")


def join_weather(bike: pd.DataFrame, weather: pd.DataFrame) -> pd.DataFrame:
    return bike.merge(weather, how="inner", on="weather_id")


def mean_count_by_weather(joined: pd.DataFrame) -> pd.Series:
    return joined.groupby("weather")["cnt"].mean().sort_values(ascending=False)
