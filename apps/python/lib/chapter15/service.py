from collections.abc import Callable

from lib.chapter15.domain import (
    ModelNotFoundError,
    ModelStore,
    Movie,
    Passenger,
    SalesPrediction,
    SurvivalPrediction,
)


class PredictionService:
    def __init__(self, store: ModelStore) -> None:
        self.store = store

    def predict_sales(self, movie: Movie) -> SalesPrediction:
        model = self.store.load_sales_model()
        return SalesPrediction(sales=model.predict_sales(movie))

    def predict_survival(self, passenger: Passenger) -> SurvivalPrediction:
        model = self.store.load_survival_model()
        return SurvivalPrediction.from_probability(
            model.survival_probability(passenger)
        )

    def health(self) -> dict[str, bool]:
        return {
            "cinema": self._can_load(self.store.load_sales_model),
            "survived": self._can_load(self.store.load_survival_model),
        }

    @staticmethod
    def _can_load(load: Callable[[], object]) -> bool:
        try:
            load()
        except ModelNotFoundError:
            return False
        return True
