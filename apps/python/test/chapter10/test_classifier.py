import pandas as pd

from lib.chapter02.iris_preprocessing import TrainTestSplit
from lib.chapter03.decision_tree import DecisionTree
from lib.chapter10.classifier import Classifier, Score, evaluate
from lib.chapter10.logistic_regression import LogisticRegression
from lib.chapter10.random_forest import RandomForest


class AlwaysSetosa:
    def fit(self, x: pd.DataFrame, t: pd.Series) -> "AlwaysSetosa":
        return self

    def predict(self, x: pd.DataFrame) -> list[str]:
        return ["setosa"] * len(x)


def small_split() -> TrainTestSplit:
    return TrainTestSplit(
        x_train=pd.DataFrame({"花弁幅": [0.1, 0.2, 0.8, 0.9]}),
        x_test=pd.DataFrame({"花弁幅": [0.15, 0.25]}),
        t_train=pd.Series(["setosa", "setosa", "virginica", "virginica"]),
        t_test=pd.Series(["setosa", "setosa"]),
    )


class TestEvaluate:
    def test_学習させてから訓練データとテストデータの正解率を求める(self) -> None:
        assert evaluate(AlwaysSetosa(), small_split()) == Score(train=0.5, test=1.0)

    def test_第3章の決定木と自作のモデルを同じ関数で評価できる(self) -> None:
        models: list[Classifier] = [
            DecisionTree(max_depth=1),
            LogisticRegression(),
            RandomForest(n_estimators=5, max_features=1, seed=0),
        ]

        scores = [evaluate(model, small_split()) for model in models]

        assert scores == [Score(train=1.0, test=1.0)] * 3
