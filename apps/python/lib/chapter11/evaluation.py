import math
from collections.abc import Callable, Iterable
from dataclasses import dataclass
from typing import Protocol

import numpy as np
import numpy.typing as npt
import pandas as pd


@dataclass(frozen=True)
class ConfusionMatrix:
    tp: int
    fp: int
    fn: int
    tn: int


def confusion_matrix(
    actual: Iterable[object], predicted: Iterable[object], positive: object
) -> ConfusionMatrix:
    pairs = [
        (a == positive, p == positive) for a, p in zip(actual, predicted, strict=True)
    ]
    return ConfusionMatrix(
        tp=pairs.count((True, True)),
        fp=pairs.count((False, True)),
        fn=pairs.count((True, False)),
        tn=pairs.count((False, False)),
    )


def ratio(numerator: float, denominator: float) -> float:
    return numerator / denominator if denominator else 0.0


def precision(cm: ConfusionMatrix) -> float:
    return ratio(cm.tp, cm.tp + cm.fp)


def recall(cm: ConfusionMatrix) -> float:
    return ratio(cm.tp, cm.tp + cm.fn)


def f1_score(cm: ConfusionMatrix) -> float:
    p, r = precision(cm), recall(cm)
    return ratio(2 * p * r, p + r)


def errors(actual: npt.ArrayLike, predicted: npt.ArrayLike) -> npt.NDArray[np.float64]:
    return np.subtract(
        np.asarray(predicted, dtype=np.float64), np.asarray(actual, dtype=np.float64)
    )


def mean_squared_error(actual: npt.ArrayLike, predicted: npt.ArrayLike) -> float:
    return float(np.mean(errors(actual, predicted) ** 2))


def root_mean_squared_error(actual: npt.ArrayLike, predicted: npt.ArrayLike) -> float:
    return math.sqrt(mean_squared_error(actual, predicted))


def mean_absolute_error(actual: npt.ArrayLike, predicted: npt.ArrayLike) -> float:
    return float(np.mean(np.abs(errors(actual, predicted))))


@dataclass(frozen=True)
class Fold:
    train: npt.NDArray[np.intp]
    test: npt.NDArray[np.intp]


def k_fold(n_samples: int, n_splits: int, seed: int) -> list[Fold]:
    positions = np.random.default_rng(seed).permutation(n_samples)
    tests = np.array_split(positions, n_splits)
    return [Fold(train=np.setdiff1d(positions, test), test=test) for test in tests]


Metric = Callable[[npt.ArrayLike, npt.ArrayLike], float]


def accuracy(actual: npt.ArrayLike, predicted: npt.ArrayLike) -> float:
    return float(np.mean(np.asarray(actual) == np.asarray(predicted)))


def classification_metric(
    score: Callable[[ConfusionMatrix], float], positive: object
) -> Metric:
    def metric(actual: npt.ArrayLike, predicted: npt.ArrayLike) -> float:
        cm = confusion_matrix(
            np.asarray(actual).tolist(), np.asarray(predicted).tolist(), positive
        )
        return score(cm)

    return metric


class Model(Protocol):
    def fit(self, x: pd.DataFrame, t: pd.Series) -> object: ...

    def predict(self, x: pd.DataFrame) -> npt.ArrayLike: ...


def cross_validate(
    make_model: Callable[[], Model],
    x: pd.DataFrame,
    t: pd.Series,
    folds: list[Fold],
    metric: Metric,
) -> list[float]:
    scores = []
    for fold in folds:
        model = make_model()
        model.fit(x.iloc[fold.train], t.iloc[fold.train])
        scores.append(metric(t.iloc[fold.test], model.predict(x.iloc[fold.test])))
    return scores
