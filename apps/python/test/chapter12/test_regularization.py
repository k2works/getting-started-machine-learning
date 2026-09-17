from dataclasses import FrozenInstanceError

import numpy as np
import pytest
from numpy.typing import NDArray
from sklearn.linear_model import Lasso, Ridge

from lib.chapter12.regularization import (
    Experiment,
    RidgeModel,
    best_experiment,
    fit_ridge,
    predict,
    run_ridge_experiments,
    zero_coefficient_names,
)


def random_dataset() -> tuple[NDArray[np.float64], NDArray[np.float64]]:
    rng = np.random.default_rng(0)
    x = rng.normal(size=(30, 4))
    t = x @ np.array([1.5, -2.0, 0.5, 3.0]) + rng.normal(scale=0.5, size=30)
    return x, t


class TestFitRidge:
    def test_alphaが0なら最小二乗法と同じ係数と切片になる(self) -> None:
        x = np.array([[1.0], [2.0], [3.0]])
        t = np.array([3.0, 5.0, 7.0])

        model = fit_ridge(x, t, alpha=0.0)

        assert model.coef == pytest.approx([2.0])
        assert model.intercept == pytest.approx(1.0)

    def test_特徴量が2つでも係数と切片を求める(self) -> None:
        x = np.array([[1.0, 0.0], [0.0, 1.0], [1.0, 1.0], [2.0, 1.0]])
        t = 3.0 * x[:, 0] - 1.0 * x[:, 1] + 4.0

        model = fit_ridge(x, t, alpha=0.0)

        assert model.coef == pytest.approx([3.0, -1.0])
        assert model.intercept == pytest.approx(4.0)

    def test_alphaを大きくすると係数の絶対値の合計が小さくなる(self) -> None:
        x, t = random_dataset()

        weak = fit_ridge(x, t, alpha=0.1)
        strong = fit_ridge(x, t, alpha=100.0)

        assert np.abs(strong.coef).sum() < np.abs(weak.coef).sum()

    def test_scikit_learnのRidgeと同じ係数と切片になる(self) -> None:
        x, t = random_dataset()

        model = fit_ridge(x, t, alpha=1.0)
        expected = Ridge(alpha=1.0).fit(x, t)

        assert model.coef == pytest.approx(expected.coef_)
        assert model.intercept == pytest.approx(expected.intercept_)


class TestPredict:
    def test_係数と切片から予測値を計算する(self) -> None:
        model = RidgeModel(coef=np.array([3.0, -1.0]), intercept=4.0)

        y = predict(model, np.array([[1.0, 2.0], [0.0, 0.0]]))

        assert y == pytest.approx([5.0, 4.0])


class TestRunRidgeExperiments:
    def test_正則化の強さごとに1件ずつ実験結果を記録する(self) -> None:
        x, t = random_dataset()

        experiments = run_ridge_experiments(
            x[:20], t[:20], x[20:], t[20:], alphas=[0.1, 1.0, 10.0]
        )

        assert [e.alpha for e in experiments] == [0.1, 1.0, 10.0]

    def test_実験結果は後から書き換えられない(self) -> None:
        x, t = random_dataset()

        experiments = run_ridge_experiments(x[:20], t[:20], x[20:], t[20:], [1.0])

        with pytest.raises(FrozenInstanceError):
            experiments[0].alpha = 2.0  # type: ignore[misc]


def experiment(alpha: float, validation_score: float) -> Experiment:
    return Experiment(
        alpha=alpha,
        train_score=0.9,
        validation_score=validation_score,
        coef_abs_sum=1.0,
    )


class TestBestExperiment:
    def test_検証データの決定係数が最も高い実験を選ぶ(self) -> None:
        experiments = [experiment(0.1, 0.7), experiment(1.0, 0.6)]

        assert best_experiment(experiments).alpha == 0.1

    def test_最も高い実験が途中にあってもそれを選ぶ(self) -> None:
        experiments = [
            experiment(0.1, 0.5),
            experiment(1.0, 0.8),
            experiment(10.0, 0.6),
        ]

        assert best_experiment(experiments).alpha == 1.0


class TestZeroCoefficients:
    def test_0になった係数の特徴量名を返す(self) -> None:
        coef = np.array([0.0, 1.5, 0.0])

        assert zero_coefficient_names(coef, ["RM", "LSTAT", "RM^2"]) == ["RM", "RM^2"]

    def test_ラッソ回帰では予測に役立たない特徴量の係数が0になる(self) -> None:
        rng = np.random.default_rng(0)
        x = rng.normal(size=(50, 4))
        t = x @ np.array([3.0, -2.0, 0.0, 0.0]) + rng.normal(scale=0.1, size=50)
        names = ["x1", "x2", "noise1", "noise2"]

        model = Lasso(alpha=0.5).fit(x, t)

        assert zero_coefficient_names(model.coef_, names) == ["noise1", "noise2"]
