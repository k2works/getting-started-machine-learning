import pandas as pd
import pytest

from lib.chapter03.decision_tree import DecisionTree, Leaf
from lib.chapter10.feature_importance import forest_importances, tree_importances
from lib.chapter10.random_forest import RandomForest


class TestTreeImportances:
    def test_分割しない木はすべての特徴量の重要度が0(self) -> None:
        x = pd.DataFrame({"がく片幅": [0.3, 0.5], "花弁幅": [0.1, 0.2]})
        t = pd.Series(["setosa", "setosa"])

        assert tree_importances(Leaf(label="setosa"), x, t) == {
            "がく片幅": 0.0,
            "花弁幅": 0.0,
        }

    def test_1回だけ分割する木は分割に使った特徴量の重要度が1(self) -> None:
        x = pd.DataFrame(
            {"がく片幅": [0.3, 0.5, 0.4, 0.6], "花弁幅": [0.1, 0.2, 0.8, 0.9]}
        )
        t = pd.Series(["setosa", "setosa", "virginica", "virginica"])
        model = DecisionTree().fit(x, t)
        assert model.tree is not None

        assert tree_importances(model.tree, x, t) == {"がく片幅": 0.0, "花弁幅": 1.0}

    def test_分割で減った不純度を件数で重み付けして割合にする(self) -> None:
        x = pd.DataFrame(
            {
                "花弁長さ": [0.1, 0.2, 0.3, 0.8, 0.7, 0.9],
                "花弁幅": [0.1, 0.1, 0.1, 0.2, 0.9, 0.9],
            }
        )
        t = pd.Series(
            ["setosa", "setosa", "setosa", "versicolor", "virginica", "virginica"]
        )
        model = DecisionTree().fit(x, t)
        assert model.tree is not None

        importances = tree_importances(model.tree, x, t)

        assert importances == pytest.approx({"花弁長さ": 7 / 11, "花弁幅": 4 / 11})


class TestForestImportances:
    def test_木が1本なら学習に使った行でのその木の重要度と一致する(self) -> None:
        x = pd.DataFrame(
            {
                "がく片幅": [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4],
                "花弁幅": [0.1, 0.12, 0.14, 0.16, 0.8, 0.82, 0.84, 0.86],
            }
        )
        t = pd.Series(["setosa"] * 4 + ["virginica"] * 4)
        forest = RandomForest(n_estimators=1, max_features=2, seed=0).fit(x, t)
        columns, tree = forest.trees[0]
        rows = forest.bootstrap_rows[0]
        assert tree.tree is not None

        expected = tree_importances(tree.tree, x.iloc[rows][columns], t.iloc[rows])

        assert forest_importances(forest, x, t) == pytest.approx(expected)
