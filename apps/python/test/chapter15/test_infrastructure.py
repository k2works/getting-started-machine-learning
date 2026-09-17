from pathlib import Path

import pandas as pd
import pytest

from lib.chapter07.cinema_regression import LinearModel
from lib.chapter08.survived_classifier import FEATURES, build_pipeline
from lib.chapter15.domain import ModelNotFoundError, Movie, Passenger
from lib.chapter15.infrastructure import JoblibModelStore


def passenger(sex: str) -> Passenger:
    return Passenger(
        pclass=2, sex=sex, age=None, sib_sp=0, parch=0, fare=20.0, embarked=None
    )


def fictional_passengers() -> tuple[pd.DataFrame, pd.Series]:
    x = pd.DataFrame(
        [
            (1, "female", 25.0, 0, 0, 60.0, "C"),
            (2, "female", 35.0, 1, 0, 30.0, "S"),
            (3, "female", 18.0, 0, 1, 10.0, "Q"),
            (3, "female", None, 0, 0, 9.0, "S"),
            (1, "male", 40.0, 1, 0, 55.0, "C"),
            (2, "male", 28.0, 0, 0, 15.0, "S"),
            (3, "male", 22.0, 0, 0, 8.0, None),
            (3, "male", None, 0, 0, 7.0, "S"),
        ],
        columns=FEATURES,
    )
    t = pd.Series([1, 1, 1, 1, 0, 0, 0, 0])
    return x, t


class TestSalesModel:
    def test_保存した線形回帰モデルを読み込んで興行収入を予測する(
        self, tmp_path: Path
    ) -> None:
        store = JoblibModelStore(tmp_path)
        store.save_sales_model(
            LinearModel(
                intercept=100.0,
                coefficients={"SNS1": 1.0, "SNS2": 2.0, "actor": 0.5, "original": 10.0},
            )
        )

        model = store.load_sales_model()

        movie = Movie(sns1=10.0, sns2=20.0, actor=100.0, original=1)
        assert model.predict_sales(movie) == pytest.approx(210.0)

    def test_モデルファイルが無ければModelNotFoundErrorを送出する(
        self, tmp_path: Path
    ) -> None:
        store = JoblibModelStore(tmp_path)

        with pytest.raises(ModelNotFoundError) as error:
            store.load_sales_model()

        assert error.value.model == "cinema"
        assert str(tmp_path) not in str(error.value)


class TestSurvivalModel:
    def test_保存したパイプラインを読み込んで生存確率を予測する(
        self, tmp_path: Path
    ) -> None:
        store = JoblibModelStore(tmp_path)
        x, t = fictional_passengers()
        store.save_survival_model(
            build_pipeline(max_depth=2, class_weight=None).fit(x, t)
        )

        model = store.load_survival_model()

        assert model.survival_probability(passenger("female")) == pytest.approx(1.0)
        assert model.survival_probability(passenger("male")) == pytest.approx(0.0)

    def test_モデルファイルが無ければModelNotFoundErrorを送出する(
        self, tmp_path: Path
    ) -> None:
        store = JoblibModelStore(tmp_path)

        with pytest.raises(ModelNotFoundError) as error:
            store.load_survival_model()

        assert error.value.model == "survived"
        assert str(tmp_path) not in str(error.value)
