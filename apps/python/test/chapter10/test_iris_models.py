import numpy as np
import pytest
from sklearn.ensemble import RandomForestClassifier
from sklearn.linear_model import LogisticRegression as LibraryLogisticRegression
from sklearn.tree import DecisionTreeClassifier

from lib.chapter02.iris_preprocessing import TrainTestSplit, prepare_iris
from lib.chapter03.decision_tree import DecisionTree
from lib.chapter10.__main__ import main
from lib.chapter10.classifier import evaluate
from lib.chapter10.feature_importance import tree_importances
from lib.chapter10.logistic_regression import LogisticRegression
from lib.chapter10.random_forest import RandomForest
from lib.dataset import data_dir
from test.markers import requires_data


@pytest.fixture(scope="module")
def iris_split() -> TrainTestSplit:
    return prepare_iris(data_dir() / "iris.csv", test_size=0.3, seed=0)


@requires_data("iris.csv")
class TestIrisModels:
    def test_ロジスティック回帰は正則化なしのscikit_learnと予測が一致する(
        self, iris_split: TrainTestSplit
    ) -> None:
        model = LogisticRegression().fit(iris_split.x_train, iris_split.t_train)
        library = LibraryLogisticRegression(C=np.inf, max_iter=1000)
        library.fit(iris_split.x_train, iris_split.t_train)

        assert model.predict(iris_split.x_test) == list(
            library.predict(iris_split.x_test)
        )

    def test_ロジスティック回帰はテストデータの45件中40件を正しく分類する(
        self, iris_split: TrainTestSplit
    ) -> None:
        score = evaluate(LogisticRegression(), iris_split)

        assert score.test == pytest.approx(40 / 45)

    def test_ランダムフォレストはテストデータの45件中39件を正しく分類する(
        self, iris_split: TrainTestSplit
    ) -> None:
        model = RandomForest(n_estimators=100, max_features=2, seed=0)

        score = evaluate(model, iris_split)

        assert (score.train, score.test) == (1.0, pytest.approx(39 / 45))

    def test_scikit_learnのランダムフォレストはテストデータの45件中40件を正しく分類する(
        self, iris_split: TrainTestSplit
    ) -> None:
        library = RandomForestClassifier(n_estimators=100, random_state=0)
        library.fit(iris_split.x_train, iris_split.t_train)

        assert library.score(iris_split.x_test, iris_split.t_test) == pytest.approx(
            40 / 45
        )

    def test_深さ3の決定木の重要度はscikit_learnと一致する(
        self, iris_split: TrainTestSplit
    ) -> None:
        model = DecisionTree(max_depth=3).fit(iris_split.x_train, iris_split.t_train)
        library = DecisionTreeClassifier(max_depth=3, random_state=0)
        library.fit(iris_split.x_train, iris_split.t_train)
        assert model.tree is not None

        importances = tree_importances(
            model.tree, iris_split.x_train, iris_split.t_train
        )

        assert list(importances.values()) == pytest.approx(
            list(library.feature_importances_)
        )

    def test_実行するとモデルごとの正解率とランダムフォレストの重要度を表示する(
        self, capsys: pytest.CaptureFixture[str]
    ) -> None:
        main()

        assert capsys.readouterr().out == (
            "モデル\t訓練データ\tテストデータ\n"
            "決定木（深さ 2）\t0.9333\t0.9556\n"
            "ロジスティック回帰\t0.9238\t0.8889\n"
            "ランダムフォレスト（100 本）\t1.0000\t0.8667\n"
            "ランダムフォレスト（100 本・深さ 2）\t0.9429\t0.9556\n"
            "\n"
            "ランダムフォレスト（100 本）の特徴量の重要度:\n"
            "がく片長さ\t0.1942\n"
            "がく片幅\t0.1257\n"
            "花弁長さ\t0.1904\n"
            "花弁幅\t0.4897\n"
        )
