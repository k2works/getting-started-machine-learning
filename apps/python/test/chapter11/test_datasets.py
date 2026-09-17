import pandas as pd

from lib.chapter11.datasets import prepare_cinema, prepare_survived


class TestPrepareSurvived:
    def test_客室クラスと年齢と男性かどうかを特徴量にし生存を正解ラベルにする(
        self,
    ) -> None:
        df = pd.DataFrame(
            {
                "PassengerId": [1, 2],
                "Survived": [0, 1],
                "Pclass": [3, 1],
                "Sex": ["male", "female"],
                "Age": [30.0, 40.0],
                "Fare": [8.0, 60.0],
            }
        )

        x, t = prepare_survived(df)

        assert x.to_dict(orient="list") == {
            "Pclass": [3, 1],
            "Age": [30.0, 40.0],
            "male": [1, 0],
        }
        assert t.to_list() == [0, 1]

    def test_年齢の欠損値を年齢の平均値で補完する(self) -> None:
        df = pd.DataFrame(
            {
                "Survived": [0, 1, 1],
                "Pclass": [3, 1, 2],
                "Sex": ["male", "female", "female"],
                "Age": [20.0, None, 40.0],
            }
        )

        x, _ = prepare_survived(df)

        assert x["Age"].to_list() == [20.0, 30.0, 40.0]


class TestPrepareCinema:
    def test_興行収入を正解ラベルにし残りの数値列の欠損値を平均値で補完する(
        self,
    ) -> None:
        df = pd.DataFrame(
            {
                "cinema_id": [101, 102, 103],
                "SNS1": [100.0, None, 300.0],
                "SNS2": [500.0, 600.0, 700.0],
                "actor": [None, 20.0, 40.0],
                "original": [0, 1, 0],
                "sales": [9000, 9500, 10000],
            }
        )

        x, t = prepare_cinema(df)

        assert x.to_dict(orient="list") == {
            "SNS1": [100.0, 200.0, 300.0],
            "SNS2": [500.0, 600.0, 700.0],
            "actor": [30.0, 20.0, 40.0],
            "original": [0, 1, 0],
        }
        assert t.to_list() == [9000, 9500, 10000]
