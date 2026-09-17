from dataclasses import dataclass
from pathlib import Path
from typing import Self

import joblib
import pandas as pd
from sklearn.base import BaseEstimator, TransformerMixin
from sklearn.pipeline import Pipeline
from sklearn.tree import DecisionTreeClassifier

from lib.chapter02.iris_preprocessing import TrainTestSplit

FEATURES = ["Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked"]
TARGET = "Survived"
MODEL_FILE = Path(__file__).resolve().parents[2] / "model" / "survived.joblib"


def load_survived(csv_file: Path) -> pd.DataFrame:
    return pd.read_csv(csv_file)


def split_features_and_target(df: pd.DataFrame) -> tuple[pd.DataFrame, pd.Series]:
    return df[FEATURES], df[TARGET]


class GroupMedianImputer(TransformerMixin, BaseEstimator):
    def __init__(self, column: str, by: tuple[str, ...]) -> None:
        self.column = column
        self.by = by

    def fit(self, x: pd.DataFrame, y: object = None) -> Self:
        self.medians_ = x.groupby(list(self.by))[self.column].median()
        self.overall_median_ = float(x[self.column].median())
        return self

    def transform(self, x: pd.DataFrame) -> pd.DataFrame:
        keys = pd.MultiIndex.from_frame(x[list(self.by)])
        medians = pd.Series(self.medians_.reindex(keys).to_numpy(), index=x.index)
        filled = x[self.column].fillna(medians).fillna(self.overall_median_)
        return x.assign(**{self.column: filled})


class MostFrequentImputer(TransformerMixin, BaseEstimator):
    def __init__(self, column: str) -> None:
        self.column = column

    def fit(self, x: pd.DataFrame, y: object = None) -> Self:
        self.most_frequent_ = x[self.column].mode()[0]
        return self

    def transform(self, x: pd.DataFrame) -> pd.DataFrame:
        return x.assign(**{self.column: x[self.column].fillna(self.most_frequent_)})


class DummyEncoder(TransformerMixin, BaseEstimator):
    def __init__(self, columns: tuple[str, ...]) -> None:
        self.columns = columns

    def fit(self, x: pd.DataFrame, y: object = None) -> Self:
        encoded = pd.get_dummies(
            x, columns=list(self.columns), drop_first=True, dtype=int
        )
        self.feature_names_ = list(encoded.columns)
        return self

    def transform(self, x: pd.DataFrame) -> pd.DataFrame:
        encoded = pd.get_dummies(x, columns=list(self.columns), dtype=int)
        return encoded.reindex(columns=self.feature_names_, fill_value=0)


def build_pipeline(max_depth: int, class_weight: str | None) -> Pipeline:
    return Pipeline(
        [
            ("age", GroupMedianImputer(column="Age", by=("Pclass", "Sex"))),
            ("embarked", MostFrequentImputer(column="Embarked")),
            ("dummies", DummyEncoder(columns=("Sex", "Embarked"))),
            (
                "model",
                DecisionTreeClassifier(
                    max_depth=max_depth, class_weight=class_weight, random_state=0
                ),
            ),
        ]
    )


def save_model(pipeline: Pipeline, model_file: Path) -> None:
    model_file.parent.mkdir(parents=True, exist_ok=True)
    joblib.dump(pipeline, model_file)


def load_model(model_file: Path) -> Pipeline:
    pipeline: Pipeline = joblib.load(model_file)
    return pipeline


@dataclass(frozen=True)
class Evaluation:
    train_accuracy: float
    test_accuracy: float
    found_survivors: int
    survivors: int


def evaluate(pipeline: Pipeline, split: TrainTestSplit) -> Evaluation:
    predictions = pipeline.predict(split.x_test)
    actual = split.t_test.to_numpy()
    return Evaluation(
        train_accuracy=float(pipeline.score(split.x_train, split.t_train)),
        test_accuracy=float(pipeline.score(split.x_test, split.t_test)),
        found_survivors=int(((predictions == 1) & (actual == 1)).sum()),
        survivors=int((actual == 1).sum()),
    )
