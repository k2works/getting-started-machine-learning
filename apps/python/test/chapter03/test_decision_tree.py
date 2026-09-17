import pandas as pd
import pytest
from sklearn.tree import DecisionTreeClassifier

from lib.chapter01.kinoko_takenoko import accuracy
from lib.chapter02.iris_preprocessing import TrainTestSplit, prepare_iris
from lib.chapter03.__main__ import main
from lib.chapter03.decision_tree import (
    DecisionTree,
    Leaf,
    Node,
    Split,
    best_split,
    format_tree,
    gini,
)
from lib.dataset import data_dir
from test.markers import requires_data


class TestGini:
    def test_1種類のラベルだけならジニ不純度は0(self) -> None:
        assert gini(["Iris-setosa", "Iris-setosa", "Iris-setosa"]) == 0.0

    def test_2種類のラベルが半分ずつならジニ不純度は05(self) -> None:
        assert gini(["Iris-setosa", "Iris-virginica"]) == 0.5

    def test_3種類のラベルが同じ数ならジニ不純度は3分の2(self) -> None:
        labels = ["Iris-setosa", "Iris-versicolor", "Iris-virginica"]

        assert gini(labels) == pytest.approx(2 / 3)


class TestBestSplit:
    def test_ラベルを完全に分けられる境界を見つける(self) -> None:
        x = pd.DataFrame({"花弁幅": [0.1, 0.2, 0.7, 0.8]})
        t = pd.Series(["setosa", "setosa", "virginica", "virginica"])

        split = best_split(x, t)

        assert split is not None
        assert (split.feature, split.threshold, split.impurity) == (
            "花弁幅",
            pytest.approx(0.45),
            0.0,
        )

    def test_複数の特徴量から不純度が最も小さくなる特徴量と境界を選ぶ(self) -> None:
        x = pd.DataFrame(
            {
                "がく片長さ": [0.1, 0.3, 0.2, 0.4],
                "花弁長さ": [0.2, 0.1, 0.9, 0.6],
            }
        )
        t = pd.Series(["setosa", "setosa", "virginica", "virginica"])

        split = best_split(x, t)

        assert split is not None
        assert (split.feature, split.threshold, split.impurity) == (
            "花弁長さ",
            pytest.approx(0.4),
            0.0,
        )

    def test_ラベルが1種類なら分割しない(self) -> None:
        x = pd.DataFrame({"花弁幅": [0.1, 0.2, 0.7]})
        t = pd.Series(["setosa", "setosa", "setosa"])

        assert best_split(x, t) is None


class TestDecisionTree:
    def test_1種類のラベルだけを学習するとそのラベルを予測する(self) -> None:
        x = pd.DataFrame({"花弁幅": [0.1, 0.2]})
        t = pd.Series(["setosa", "setosa"])

        model = DecisionTree().fit(x, t)

        assert model.predict(pd.DataFrame({"花弁幅": [0.15, 0.9]})) == [
            "setosa",
            "setosa",
        ]

    def test_境界の左右で異なるラベルを予測する(self) -> None:
        x = pd.DataFrame({"花弁幅": [0.1, 0.2, 0.7, 0.8]})
        t = pd.Series(["setosa", "setosa", "virginica", "virginica"])

        model = DecisionTree().fit(x, t)

        assert model.predict(pd.DataFrame({"花弁幅": [0.15, 0.75]})) == [
            "setosa",
            "virginica",
        ]


def three_species() -> tuple[pd.DataFrame, pd.Series]:
    x = pd.DataFrame({"花弁幅": [0.1, 0.2, 0.3, 0.5, 0.6, 0.9]})
    t = pd.Series(
        ["setosa", "setosa", "setosa", "versicolor", "versicolor", "virginica"]
    )
    return x, t


class TestMaxDepth:
    def test_深さを制限しなければすべての訓練データを分け切る(self) -> None:
        x, t = three_species()

        model = DecisionTree().fit(x, t)

        assert model.predict(x) == t.to_list()

    def test_深さを1に制限すると境界の先は多数派のラベルを予測する(self) -> None:
        x, t = three_species()

        model = DecisionTree(max_depth=1).fit(x, t)

        assert model.predict(pd.DataFrame({"花弁幅": [0.2, 0.95]})) == [
            "setosa",
            "versicolor",
        ]

    def test_学習する前に予測するとエラーになる(self) -> None:
        with pytest.raises(ValueError, match="fit で学習してから"):
            DecisionTree().predict(pd.DataFrame({"花弁幅": [0.1]}))


@pytest.fixture(scope="module")
def iris_split() -> TrainTestSplit:
    return prepare_iris(data_dir() / "iris.csv", test_size=0.3, seed=0)


@requires_data("iris.csv")
class TestIrisData:
    def test_深さ2の決定木はテストデータの45件中43件を正しく分類する(
        self, iris_split: TrainTestSplit
    ) -> None:
        model = DecisionTree(max_depth=2).fit(iris_split.x_train, iris_split.t_train)

        predictions = model.predict(iris_split.x_test)

        assert accuracy(predictions, iris_split.t_test.to_list()) == pytest.approx(
            43 / 45
        )

    @pytest.mark.parametrize("max_depth", [1, 2, 3, None])
    def test_scikit_learnの決定木とテストデータの予測が一致する(
        self, iris_split: TrainTestSplit, max_depth: int | None
    ) -> None:
        model = DecisionTree(max_depth=max_depth)
        library = DecisionTreeClassifier(max_depth=max_depth, random_state=0)

        model.fit(iris_split.x_train, iris_split.t_train)
        library.fit(iris_split.x_train, iris_split.t_train)

        assert model.predict(iris_split.x_test) == list(
            library.predict(iris_split.x_test)
        )

    def test_実行すると深さごとの正解率と深さ2の決定木を表示する(
        self, capsys: pytest.CaptureFixture[str]
    ) -> None:
        main()

        assert capsys.readouterr().out == (
            "深さ\t訓練データ\tテストデータ\n"
            "1\t0.6857\t0.6222\n"
            "2\t0.9333\t0.9556\n"
            "3\t0.9619\t0.9111\n"
            "4\t0.9714\t0.9111\n"
            "5\t0.9714\t0.9111\n"
            "制限なし\t1.0000\t0.9111\n"
            "\n"
            "深さ 2 の決定木:\n"
            "花弁幅 <= 0.2950\n"
            "  Iris-setosa\n"
            "花弁幅 > 0.2950\n"
            "  花弁幅 <= 0.6500\n"
            "    Iris-versicolor\n"
            "  花弁幅 > 0.6500\n"
            "    Iris-virginica\n"
        )


class TestFormatTree:
    def test_葉だけの木はラベルを表示する(self) -> None:
        assert format_tree(Leaf(label="setosa")) == "setosa"

    def test_節は条件ごとに字下げして表示する(self) -> None:
        tree = Node(
            split=Split(feature="花弁幅", threshold=0.4, impurity=0.0),
            left=Leaf(label="setosa"),
            right=Node(
                split=Split(feature="花弁長さ", threshold=0.75, impurity=0.0),
                left=Leaf(label="versicolor"),
                right=Leaf(label="virginica"),
            ),
        )

        assert format_tree(tree) == (
            "花弁幅 <= 0.4000\n"
            "  setosa\n"
            "花弁幅 > 0.4000\n"
            "  花弁長さ <= 0.7500\n"
            "    versicolor\n"
            "  花弁長さ > 0.7500\n"
            "    virginica"
        )
