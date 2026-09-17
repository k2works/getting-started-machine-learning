from collections.abc import Sequence
from dataclasses import dataclass

import numpy as np
from numpy.typing import NDArray
from sklearn.metrics import r2_score


@dataclass(frozen=True)
class RidgeModel:
    coef: NDArray[np.float64]
    intercept: float


@dataclass(frozen=True)
class Experiment:
    alpha: float
    train_score: float
    validation_score: float
    coef_abs_sum: float


def fit_ridge(
    x: NDArray[np.float64], t: NDArray[np.float64], alpha: float
) -> RidgeModel:
    x_mean = x.mean(axis=0)
    t_mean = float(t.mean())
    xc = x - x_mean
    tc = t - t_mean
    identity = np.eye(x.shape[1])
    coef = np.linalg.solve(xc.T @ xc + alpha * identity, xc.T @ tc)
    return RidgeModel(coef=coef, intercept=t_mean - float(x_mean @ coef))


def predict(model: RidgeModel, x: NDArray[np.float64]) -> NDArray[np.float64]:
    return x @ model.coef + model.intercept


def run_ridge_experiments(
    x_train: NDArray[np.float64],
    t_train: NDArray[np.float64],
    x_valid: NDArray[np.float64],
    t_valid: NDArray[np.float64],
    alphas: Sequence[float],
) -> tuple[Experiment, ...]:
    experiments = []
    for alpha in alphas:
        model = fit_ridge(x_train, t_train, alpha)
        experiments.append(
            Experiment(
                alpha=alpha,
                train_score=float(r2_score(t_train, predict(model, x_train))),
                validation_score=float(r2_score(t_valid, predict(model, x_valid))),
                coef_abs_sum=float(np.abs(model.coef).sum()),
            )
        )
    return tuple(experiments)


def best_experiment(experiments: Sequence[Experiment]) -> Experiment:
    return max(experiments, key=lambda e: e.validation_score)


def zero_coefficient_names(
    coef: NDArray[np.float64], feature_names: Sequence[str]
) -> list[str]:
    return [name for name, c in zip(feature_names, coef, strict=True) if c == 0.0]
