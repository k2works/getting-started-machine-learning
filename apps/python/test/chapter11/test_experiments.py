import pandas as pd
import pytest
from sklearn.model_selection import cross_validate as sklearn_cross_validate
from sklearn.tree import DecisionTreeClassifier

from lib.chapter11.__main__ import main
from lib.chapter11.datasets import prepare_survived
from lib.chapter11.evaluation import k_fold
from lib.chapter11.experiments import evaluate_cinema, evaluate_survived
from lib.dataset import data_dir
from test.markers import requires_data


@requires_data("Survived.csv")
class TestSurvivedData:
    def test_決定木を5分割交差検証で評価する(self) -> None:
        scores = evaluate_survived(data_dir() / "Survived.csv")

        assert scores == pytest.approx(
            {"正解率": 0.7733, "適合率": 0.8221, "再現率": 0.5599, "F値": 0.6485},
            abs=1e-4,
        )

    def test_同じ分割ならscikit_learnのcross_validateと同じ平均になる(self) -> None:
        x, t = prepare_survived(pd.read_csv(data_dir() / "Survived.csv"))
        folds = k_fold(n_samples=len(x), n_splits=5, seed=0)

        result = sklearn_cross_validate(
            DecisionTreeClassifier(max_depth=2, random_state=0),
            x,
            t,
            cv=[(fold.train, fold.test) for fold in folds],
            scoring=["accuracy", "precision", "recall", "f1"],
        )

        scores = evaluate_survived(data_dir() / "Survived.csv")
        assert scores["正解率"] == pytest.approx(result["test_accuracy"].mean())
        assert scores["適合率"] == pytest.approx(result["test_precision"].mean())
        assert scores["再現率"] == pytest.approx(result["test_recall"].mean())
        assert scores["F値"] == pytest.approx(result["test_f1"].mean())


@requires_data("cinema.csv")
class TestCinemaData:
    def test_線形回帰を5分割交差検証で評価する(self) -> None:
        scores = evaluate_cinema(data_dir() / "cinema.csv")

        assert scores == pytest.approx({"RMSE": 401.34, "MAE": 316.64}, abs=1e-2)


@requires_data("Survived.csv", "cinema.csv")
class TestMain:
    def test_実行すると交差検証の平均を表示する(
        self, capsys: pytest.CaptureFixture[str]
    ) -> None:
        main()

        assert capsys.readouterr().out == (
            "Survived（決定木、5 分割交差検証の平均）\n"
            "  正解率: 0.7733\n"
            "  適合率: 0.8221\n"
            "  再現率: 0.5599\n"
            "  F値: 0.6485\n"
            "cinema（線形回帰、5 分割交差検証の平均）\n"
            "  RMSE: 401.34\n"
            "  MAE: 316.64\n"
        )
