from lib.chapter15.domain import ModelNotFoundError, Movie, Passenger


class StubSalesModel:
    def predict_sales(self, movie: Movie) -> float:
        return 1000.0 + movie.sns1


class StubSurvivalModel:
    def survival_probability(self, passenger: Passenger) -> float:
        return 0.8 if passenger.sex == "female" else 0.2


class StubModelStore:
    def load_sales_model(self) -> StubSalesModel:
        return StubSalesModel()

    def load_survival_model(self) -> StubSurvivalModel:
        return StubSurvivalModel()


class EmptyModelStore:
    def load_sales_model(self) -> StubSalesModel:
        raise ModelNotFoundError("cinema")

    def load_survival_model(self) -> StubSurvivalModel:
        raise ModelNotFoundError("survived")
