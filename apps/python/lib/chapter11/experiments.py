from collections.abc import Callable
from pathlib import Path
from statistics import mean

import pandas as pd
from sklearn.linear_model import LinearRegression
from sklearn.tree import DecisionTreeClassifier

from lib.chapter11.datasets import prepare_cinema, prepare_survived
from lib.chapter11.evaluation import (
    Metric,
    Model,
    accuracy,
    classification_metric,
    cross_validate,
    f1_score,
    k_fold,
    mean_absolute_error,
    precision,
    recall,
    root_mean_squared_error,
)

N_SPLITS = 5
SEED = 0


def make_decision_tree() -> Model:
    model: Model = DecisionTreeClassifier(max_depth=2, random_state=0)
    return model


def make_linear_regression() -> Model:
    model: Model = LinearRegression()
    return model


SURVIVED_METRICS: dict[str, Metric] = {
    "正解率": accuracy,
    "適合率": classification_metric(precision, positive=1),
    "再現率": classification_metric(recall, positive=1),
    "F値": classification_metric(f1_score, positive=1),
}

CINEMA_METRICS: dict[str, Metric] = {
    "RMSE": root_mean_squared_error,
    "MAE": mean_absolute_error,
}


def evaluate(
    make_model: Callable[[], Model],
    x: pd.DataFrame,
    t: pd.Series,
    metrics: dict[str, Metric],
) -> dict[str, float]:
    folds = k_fold(n_samples=len(x), n_splits=N_SPLITS, seed=SEED)
    return {
        name: mean(cross_validate(make_model, x, t, folds, metric))
        for name, metric in metrics.items()
    }


def evaluate_survived(csv_file: Path) -> dict[str, float]:
    x, t = prepare_survived(pd.read_csv(csv_file))
    return evaluate(make_decision_tree, x, t, SURVIVED_METRICS)


def evaluate_cinema(csv_file: Path) -> dict[str, float]:
    x, t = prepare_cinema(pd.read_csv(csv_file))
    return evaluate(make_linear_regression, x, t, CINEMA_METRICS)
