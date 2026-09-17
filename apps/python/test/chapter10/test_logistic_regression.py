import numpy as np
import pandas as pd
import pytest

from lib.chapter10.logistic_regression import LogisticRegression, softmax


class TestSoftmax:
    def test_値がすべて同じなら確率は均等になる(self) -> None:
        probabilities = softmax(np.array([[0.0, 0.0, 0.0, 0.0]]))

        assert probabilities.tolist() == [[0.25, 0.25, 0.25, 0.25]]

    def test_値の差が指数の比になる(self) -> None:
        probabilities = softmax(np.array([[0.0, np.log(2.0)], [np.log(3.0), 0.0]]))

        assert probabilities == pytest.approx(
            np.array([[1 / 3, 2 / 3], [3 / 4, 1 / 4]])
        )

    def test_大きな値でもあふれずに確率を求める(self) -> None:
        probabilities = softmax(np.array([[1000.0, 1000.0]]))

        assert probabilities.tolist() == [[0.5, 0.5]]


class TestLogisticRegression:
    def test_1種類のラベルだけを学習するとそのラベルを予測する(self) -> None:
        x = pd.DataFrame({"花弁幅": [0.1, 0.2]})
        t = pd.Series(["setosa", "setosa"])

        model = LogisticRegression().fit(x, t)

        assert model.predict(pd.DataFrame({"花弁幅": [0.15, 0.9]})) == [
            "setosa",
            "setosa",
        ]

    def test_2種類のラベルを境界の左右で予測する(self) -> None:
        x = pd.DataFrame({"花弁幅": [0.1, 0.2, 0.8, 0.9]})
        t = pd.Series(["setosa", "setosa", "virginica", "virginica"])

        model = LogisticRegression().fit(x, t)

        assert model.predict(pd.DataFrame({"花弁幅": [0.15, 0.85]})) == [
            "setosa",
            "virginica",
        ]

    def test_3種類のラベルを2つの特徴量から予測する(self) -> None:
        x = pd.DataFrame(
            {
                "花弁長さ": [0.1, 0.2, 0.5, 0.6, 0.5, 0.6],
                "花弁幅": [0.1, 0.2, 0.1, 0.2, 0.8, 0.9],
            }
        )
        t = pd.Series(
            ["setosa", "setosa", "versicolor", "versicolor", "virginica", "virginica"]
        )

        model = LogisticRegression().fit(x, t)

        assert model.predict(x) == t.to_list()

    def test_学習を繰り返すと損失が小さくなる(self) -> None:
        x = pd.DataFrame({"花弁幅": [0.1, 0.2, 0.8, 0.9]})
        t = pd.Series(["setosa", "setosa", "virginica", "virginica"])

        model = LogisticRegression(epochs=100).fit(x, t)

        assert len(model.losses) == 100
        assert model.losses[-1] < model.losses[0]

    def test_学習する前に予測するとエラーになる(self) -> None:
        with pytest.raises(ValueError, match="fit で学習してから"):
            LogisticRegression().predict(pd.DataFrame({"花弁幅": [0.1]}))
