from dataclasses import dataclass
from pathlib import Path

import numpy as np
import numpy.typing as npt
import pandas as pd
from sklearn.pipeline import Pipeline
from sklearn.tree import DecisionTreeClassifier

from lib.chapter02.iris_preprocessing import split_train_test
from lib.chapter08.survived_classifier import DummyEncoder, GroupMedianImputer
from lib.chapter11.evaluation import (
    accuracy,
    confusion_matrix,
    f1_score,
    precision,
    recall,
)

TARGET = "y"
UNUSED = ("id", "day")
TEST_SIZE = 0.1
VALIDATION_SIZE = 0.2
CATEGORICAL = (
    "job",
    "marital",
    "education",
    "default",
    "housing",
    "loan",
    "contact",
    "month",
)


def build_pipeline(max_depth: int) -> Pipeline:
    return Pipeline(
        [
            ("duration", GroupMedianImputer(column="duration", by=("housing", "loan"))),
            ("dummies", DummyEncoder(columns=CATEGORICAL)),
            (
                "model",
                DecisionTreeClassifier(
                    max_depth=max_depth, class_weight="balanced", random_state=0
                ),
            ),
        ]
    )


@dataclass(frozen=True)
class ThreeWaySplit:
    x_train: pd.DataFrame
    x_valid: pd.DataFrame
    x_test: pd.DataFrame
    t_train: pd.Series
    t_valid: pd.Series
    t_test: pd.Series


def split_three_way(
    x: pd.DataFrame,
    t: pd.Series,
    test_size: float,
    validation_size: float,
    seed: int,
) -> ThreeWaySplit:
    outer = split_train_test(x, t, test_size=test_size, seed=seed)
    inner = split_train_test(
        outer.x_train, outer.t_train, test_size=validation_size, seed=seed
    )
    return ThreeWaySplit(
        x_train=inner.x_train,
        x_valid=inner.x_test,
        x_test=outer.x_test,
        t_train=inner.t_train,
        t_valid=inner.t_test,
        t_test=outer.t_test,
    )


@dataclass(frozen=True)
class Scores:
    accuracy: float
    precision: float
    recall: float
    f1: float


@dataclass(frozen=True)
class DepthResult:
    max_depth: int
    train: Scores
    valid: Scores


def score(actual: npt.ArrayLike, predicted: npt.ArrayLike) -> Scores:
    cm = confusion_matrix(np.asarray(actual), np.asarray(predicted), positive=1)
    return Scores(
        accuracy=accuracy(actual, predicted),
        precision=precision(cm),
        recall=recall(cm),
        f1=f1_score(cm),
    )


def select_best(results: list[DepthResult]) -> DepthResult:
    return max(results, key=lambda r: (r.valid.f1, -r.max_depth))


def tune_max_depth(split: ThreeWaySplit, max_depths: list[int]) -> list[DepthResult]:
    results = []
    for max_depth in max_depths:
        pipeline = build_pipeline(max_depth).fit(split.x_train, split.t_train)
        results.append(
            DepthResult(
                max_depth=max_depth,
                train=score(split.t_train, pipeline.predict(split.x_train)),
                valid=score(split.t_valid, pipeline.predict(split.x_valid)),
            )
        )
    return results


def fit_final(split: ThreeWaySplit, max_depth: int) -> Pipeline:
    x = pd.concat([split.x_train, split.x_valid])
    t = pd.concat([split.t_train, split.t_valid])
    return build_pipeline(max_depth).fit(x, t)


def prepare_bank(csv_file: Path, seed: int) -> ThreeWaySplit:
    df = pd.read_csv(csv_file)
    x = df.drop(columns=[*UNUSED, TARGET])
    return split_three_way(
        x, df[TARGET], test_size=TEST_SIZE, validation_size=VALIDATION_SIZE, seed=seed
    )
