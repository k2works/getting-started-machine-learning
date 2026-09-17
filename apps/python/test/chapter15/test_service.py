import pytest

from lib.chapter15.domain import (
    ModelNotFoundError,
    Movie,
    Passenger,
    SalesPrediction,
    SurvivalPrediction,
)
from lib.chapter15.service import PredictionService
from test.chapter15.stubs import EmptyModelStore, StubModelStore

MOVIE = Movie(sns1=200.0, sns2=500.0, actor=3000.0, original=1)
PASSENGER = Passenger(
    pclass=1, sex="female", age=None, sib_sp=0, parch=0, fare=50.0, embarked="C"
)


class TestPredictSales:
    def test_映画の特徴量から興行収入を予測する(self) -> None:
        service = PredictionService(StubModelStore())

        prediction = service.predict_sales(MOVIE)

        assert prediction == SalesPrediction(sales=1200.0)

    def test_モデルが無ければModelNotFoundErrorを送出する(self) -> None:
        service = PredictionService(EmptyModelStore())

        with pytest.raises(ModelNotFoundError, match="cinema"):
            service.predict_sales(MOVIE)


class TestPredictSurvival:
    def test_生存確率が05以上なら生存と予測する(self) -> None:
        service = PredictionService(StubModelStore())

        prediction = service.predict_survival(PASSENGER)

        assert prediction == SurvivalPrediction(survived=True, probability=0.8)

    def test_生存確率が05未満なら死亡と予測する(self) -> None:
        service = PredictionService(StubModelStore())

        prediction = service.predict_survival(
            Passenger(
                pclass=3,
                sex="male",
                age=30.0,
                sib_sp=0,
                parch=0,
                fare=8.0,
                embarked="S",
            )
        )

        assert prediction == SurvivalPrediction(survived=False, probability=0.2)


class TestHealth:
    def test_モデルを読み込めればそれぞれTrueを返す(self) -> None:
        service = PredictionService(StubModelStore())

        assert service.health() == {"cinema": True, "survived": True}

    def test_モデルが無ければそれぞれFalseを返す(self) -> None:
        service = PredictionService(EmptyModelStore())

        assert service.health() == {"cinema": False, "survived": False}
