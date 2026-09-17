import numpy as np
import pandas as pd
import pytest
from sklearn import metrics
from sklearn.linear_model import LinearRegression
from sklearn.model_selection import KFold
from sklearn.model_selection import cross_validate as sklearn_cross_validate

from lib.chapter11.evaluation import (
    ConfusionMatrix,
    Fold,
    accuracy,
    classification_metric,
    confusion_matrix,
    cross_validate,
    f1_score,
    k_fold,
    mean_absolute_error,
    mean_squared_error,
    precision,
    recall,
    root_mean_squared_error,
)


class TestConfusionMatrix:
    def test_正例と負例の予測の当たり外れを数える(self) -> None:
        actual = [1, 1, 1, 0, 0]
        predicted = [1, 1, 0, 1, 0]

        assert confusion_matrix(actual, predicted, positive=1) == ConfusionMatrix(
            tp=2, fp=1, fn=1, tn=1
        )

    def test_どちらのラベルを正例とするかで数え方が変わる(self) -> None:
        actual = [1, 1, 1, 0, 0, 0]
        predicted = [1, 0, 0, 0, 0, 1]

        assert confusion_matrix(actual, predicted, positive=0) == ConfusionMatrix(
            tp=2, fp=2, fn=1, tn=1
        )


class TestPrecisionRecallF1:
    def test_適合率は正例と予測したうち本当に正例だった割合(self) -> None:
        cm = ConfusionMatrix(tp=3, fp=1, fn=2, tn=4)

        assert precision(cm) == pytest.approx(0.75)

    def test_再現率は本当の正例のうち正例と予測できた割合(self) -> None:
        cm = ConfusionMatrix(tp=3, fp=1, fn=2, tn=4)

        assert recall(cm) == pytest.approx(0.6)

    def test_F値は適合率と再現率の調和平均(self) -> None:
        cm = ConfusionMatrix(tp=3, fp=1, fn=2, tn=4)

        assert f1_score(cm) == pytest.approx(2 * 0.75 * 0.6 / (0.75 + 0.6))

    def test_正例を一件も当てられなければ適合率と再現率とF値は0(self) -> None:
        cm = ConfusionMatrix(tp=0, fp=0, fn=3, tn=5)

        assert (precision(cm), recall(cm), f1_score(cm)) == (0.0, 0.0, 0.0)


class TestRegressionMetrics:
    def test_誤差の2乗の平均と平方根と絶対値の平均を求める(self) -> None:
        actual = [3.0, 5.0, 8.0]
        predicted = [2.0, 5.0, 10.0]

        assert mean_squared_error(actual, predicted) == pytest.approx(5 / 3)
        assert root_mean_squared_error(actual, predicted) == pytest.approx(
            (5 / 3) ** 0.5
        )
        assert mean_absolute_error(actual, predicted) == pytest.approx(1.0)

    def test_大きく外れた予測があるとRMSEはMAEより大きく増える(self) -> None:
        actual = [3.0, 5.0, 8.0, 10.0]
        predicted = [2.0, 5.0, 10.0, 30.0]

        assert root_mean_squared_error(actual, predicted) == pytest.approx(101.25**0.5)
        assert mean_absolute_error(actual, predicted) == pytest.approx(5.75)


class TestKFold:
    def test_データをk個のテストデータにほぼ均等に分ける(self) -> None:
        folds = k_fold(n_samples=10, n_splits=3, seed=0)

        assert [len(fold.test) for fold in folds] == [4, 3, 3]

    def test_件数と分割数が変わってもほぼ均等に分ける(self) -> None:
        folds = k_fold(n_samples=7, n_splits=2, seed=0)

        assert [len(fold.test) for fold in folds] == [4, 3]

    def test_どの行もちょうど一度だけテストデータになる(self) -> None:
        folds = k_fold(n_samples=10, n_splits=3, seed=0)

        tested = sorted(int(i) for fold in folds for i in fold.test)
        assert tested == list(range(10))

    def test_各分割の訓練データはテストデータ以外のすべての行(self) -> None:
        folds = k_fold(n_samples=10, n_splits=3, seed=0)

        for fold in folds:
            assert set(fold.train) & set(fold.test) == set()
            assert set(fold.train) | set(fold.test) == set(range(10))

    def test_同じシードなら同じ分け方になる(self) -> None:
        first = k_fold(n_samples=10, n_splits=3, seed=42)
        second = k_fold(n_samples=10, n_splits=3, seed=42)

        assert [f.test.tolist() for f in first] == [s.test.tolist() for s in second]

    def test_シードが違えば違う分け方になる(self) -> None:
        first = k_fold(n_samples=10, n_splits=3, seed=0)
        second = k_fold(n_samples=10, n_splits=3, seed=1)

        assert [f.test.tolist() for f in first] != [s.test.tolist() for s in second]


class MeanModel:
    """訓練データの正解の平均値を常に予測するテスト用のモデル。"""

    def fit(self, x: pd.DataFrame, t: pd.Series) -> "MeanModel":
        self.mean = float(t.mean())
        return self

    def predict(self, x: pd.DataFrame) -> list[float]:
        return [self.mean] * len(x)


class TestCrossValidate:
    x = pd.DataFrame({"feature": [10, 20, 30, 40]})
    t = pd.Series([1.0, 2.0, 3.0, 4.0])
    folds = [
        Fold(train=np.array([0, 1]), test=np.array([2, 3])),
        Fold(train=np.array([2, 3]), test=np.array([0, 1])),
    ]

    def test_分割ごとに訓練データで学習してテストデータを評価する(self) -> None:
        scores = cross_validate(
            MeanModel, self.x, self.t, self.folds, mean_absolute_error
        )

        assert scores == pytest.approx([2.0, 2.0])

    def test_評価関数を差し替えると別の指標で評価する(self) -> None:
        scores = cross_validate(
            MeanModel, self.x, self.t, self.folds, mean_squared_error
        )

        assert scores == pytest.approx([4.25, 4.25])


class TestClassificationMetric:
    def test_正解率は正解と予測が一致した割合(self) -> None:
        assert accuracy([1, 0, 1, 0], [1, 1, 1, 0]) == pytest.approx(0.75)

    def test_混同行列から求める指標を正解と予測から求める評価関数に変える(self) -> None:
        actual = [1, 1, 1, 0, 0]
        predicted = [1, 0, 0, 1, 0]

        precision_metric = classification_metric(precision, positive=1)
        recall_metric = classification_metric(recall, positive=1)

        assert precision_metric(actual, predicted) == pytest.approx(0.5)
        assert recall_metric(actual, predicted) == pytest.approx(1 / 3)


class TestCompareWithScikitLearn:
    actual = [1, 0, 1, 1, 0, 1, 0, 0, 1, 1]
    predicted = [1, 0, 0, 1, 1, 1, 0, 1, 1, 0]

    def test_混同行列がscikit_learnと一致する(self) -> None:
        cm = confusion_matrix(self.actual, self.predicted, positive=1)

        [[tn, fp], [fn, tp]] = metrics.confusion_matrix(self.actual, self.predicted)
        assert cm == ConfusionMatrix(tp=tp, fp=fp, fn=fn, tn=tn)

    def test_適合率と再現率とF値がscikit_learnと一致する(self) -> None:
        cm = confusion_matrix(self.actual, self.predicted, positive=1)

        assert precision(cm) == pytest.approx(
            metrics.precision_score(self.actual, self.predicted)
        )
        assert recall(cm) == pytest.approx(
            metrics.recall_score(self.actual, self.predicted)
        )
        assert f1_score(cm) == pytest.approx(
            metrics.f1_score(self.actual, self.predicted)
        )

    def test_MSEとMAEがscikit_learnと一致する(self) -> None:
        actual = [2.5, 0.0, 2.1, 7.8]
        predicted = [3.0, -0.5, 2.0, 7.0]

        assert mean_squared_error(actual, predicted) == pytest.approx(
            metrics.mean_squared_error(actual, predicted)
        )
        assert mean_absolute_error(actual, predicted) == pytest.approx(
            metrics.mean_absolute_error(actual, predicted)
        )

    def test_分割ごとのテストデータの件数がscikit_learnのKFoldと一致する(self) -> None:
        folds = k_fold(n_samples=10, n_splits=3, seed=0)

        sklearn_folds = KFold(n_splits=3).split(np.zeros(10))
        assert [len(f.test) for f in folds] == [len(test) for _, test in sklearn_folds]

    def test_同じ分割を渡せばscikit_learnのcross_validateと同じスコアになる(
        self,
    ) -> None:
        x = pd.DataFrame({"feature": [1.0, 2.0, 3.0, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0]})
        t = pd.Series([1.2, 1.9, 3.1, 4.2, 4.8, 6.3, 6.9, 8.1, 9.2])
        folds = k_fold(n_samples=9, n_splits=3, seed=0)

        scores = cross_validate(LinearRegression, x, t, folds, mean_absolute_error)

        result = sklearn_cross_validate(
            LinearRegression(),
            x,
            t,
            cv=[(fold.train, fold.test) for fold in folds],
            scoring="neg_mean_absolute_error",
        )
        assert scores == pytest.approx(list(-result["test_score"]))
