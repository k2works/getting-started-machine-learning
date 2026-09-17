import numpy as np
import pandas as pd
import pytest

from lib.chapter03.decision_tree import DecisionTree
from lib.chapter10.random_forest import RandomForest, bootstrap_sample, majority_vote


class TestMajorityVote:
    def test_サンプルごとに最も多い予測を選ぶ(self) -> None:
        votes = [
            ["setosa", "virginica"],
            ["setosa", "virginica"],
            ["versicolor", "setosa"],
        ]

        assert majority_vote(votes) == ["setosa", "virginica"]


class TestBootstrapSample:
    def test_元のデータと同じ件数の行番号を重複を許して選ぶ(self) -> None:
        rows = bootstrap_sample(100, np.random.default_rng(0))

        assert len(rows) == 100
        assert all(0 <= row < 100 for row in rows)
        assert len(set(rows.tolist())) < 100

    def test_同じシードなら同じ行を選ぶ(self) -> None:
        first = bootstrap_sample(10, np.random.default_rng(42))
        second = bootstrap_sample(10, np.random.default_rng(42))

        assert first.tolist() == second.tolist()


def two_species() -> tuple[pd.DataFrame, pd.Series]:
    x = pd.DataFrame(
        {
            "がく片幅": [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4, 0.5, 0.3],
            "花弁幅": [0.1, 0.12, 0.14, 0.16, 0.18, 0.8, 0.82, 0.84, 0.86, 0.88],
        }
    )
    t = pd.Series(["setosa"] * 5 + ["virginica"] * 5)
    return x, t


class TestRandomForest:
    def test_指定した数だけ第3章の決定木を学習する(self) -> None:
        x, t = two_species()

        model = RandomForest(n_estimators=5, max_features=1, seed=0).fit(x, t)

        assert len(model.trees) == 5
        assert all(isinstance(tree, DecisionTree) for _, tree in model.trees)

    def test_各決定木は指定した数の特徴量だけを使う(self) -> None:
        x, t = two_species()

        model = RandomForest(n_estimators=5, max_features=1, seed=0).fit(x, t)

        assert all(len(columns) == 1 for columns, _ in model.trees)

    def test_決定木の多数決で予測する(self) -> None:
        x, t = two_species()

        model = RandomForest(n_estimators=25, max_features=2, seed=0).fit(x, t)

        assert model.predict(
            pd.DataFrame({"がく片幅": [0.4, 0.4], "花弁幅": [0.13, 0.83]})
        ) == [
            "setosa",
            "virginica",
        ]

    def test_同じシードなら同じ予測になる(self) -> None:
        x, t = two_species()

        first = RandomForest(n_estimators=5, max_features=1, seed=7).fit(x, t)
        second = RandomForest(n_estimators=5, max_features=1, seed=7).fit(x, t)

        assert [columns for columns, _ in first.trees] == [
            columns for columns, _ in second.trees
        ]
        assert first.predict(x) == second.predict(x)

    def test_学習する前に予測するとエラーになる(self) -> None:
        with pytest.raises(ValueError, match="fit で学習してから"):
            RandomForest().predict(pd.DataFrame({"花弁幅": [0.1]}))
