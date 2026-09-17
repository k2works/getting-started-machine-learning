from dataclasses import dataclass
from typing import Protocol

SURVIVAL_THRESHOLD = 0.5


@dataclass(frozen=True)
class Movie:
    sns1: float
    sns2: float
    actor: float
    original: int


@dataclass(frozen=True)
class Passenger:
    pclass: int
    sex: str
    age: float | None
    sib_sp: int
    parch: int
    fare: float
    embarked: str | None


@dataclass(frozen=True)
class SalesPrediction:
    sales: float


@dataclass(frozen=True)
class SurvivalPrediction:
    survived: bool
    probability: float

    @classmethod
    def from_probability(cls, probability: float) -> "SurvivalPrediction":
        return cls(survived=probability >= SURVIVAL_THRESHOLD, probability=probability)


class ModelNotFoundError(Exception):
    """学習済みモデルが見つからないときに送出する。"""

    def __init__(self, model: str) -> None:
        super().__init__(f"学習済みモデル {model} が見つかりません")
        self.model = model


class SalesModel(Protocol):
    def predict_sales(self, movie: Movie) -> float: ...


class SurvivalModel(Protocol):
    def survival_probability(self, passenger: Passenger) -> float: ...


class ModelStore(Protocol):
    def load_sales_model(self) -> SalesModel: ...

    def load_survival_model(self) -> SurvivalModel: ...
