from pathlib import Path
from typing import Any

import joblib
import pandas as pd
from sklearn.pipeline import Pipeline

from lib.chapter07.cinema_regression import FEATURES as CINEMA_FEATURES
from lib.chapter07.cinema_regression import LinearModel
from lib.chapter08.survived_classifier import FEATURES as SURVIVED_FEATURES
from lib.chapter15.domain import ModelNotFoundError, Movie, Passenger

SALES_MODEL = "cinema"
SURVIVAL_MODEL = "survived"


class LinearSalesModel:
    def __init__(self, model: LinearModel) -> None:
        self.model = model

    def predict_sales(self, movie: Movie) -> float:
        row = pd.DataFrame(
            [(movie.sns1, movie.sns2, movie.actor, movie.original)],
            columns=CINEMA_FEATURES,
        )
        return float(self.model.predict(row)[0])


class PipelineSurvivalModel:
    def __init__(self, pipeline: Pipeline) -> None:
        self.pipeline = pipeline

    def survival_probability(self, passenger: Passenger) -> float:
        row = pd.DataFrame(
            [
                (
                    passenger.pclass,
                    passenger.sex,
                    passenger.age,
                    passenger.sib_sp,
                    passenger.parch,
                    passenger.fare,
                    passenger.embarked,
                )
            ],
            columns=SURVIVED_FEATURES,
        )
        survived_index = list(self.pipeline.classes_).index(1)
        return float(self.pipeline.predict_proba(row)[0][survived_index])


class JoblibModelStore:
    def __init__(self, model_dir: Path) -> None:
        self.model_dir = model_dir

    def save_sales_model(self, model: LinearModel) -> None:
        self._save(model, SALES_MODEL)

    def save_survival_model(self, pipeline: Pipeline) -> None:
        self._save(pipeline, SURVIVAL_MODEL)

    def load_sales_model(self) -> LinearSalesModel:
        return LinearSalesModel(self._load(SALES_MODEL))

    def load_survival_model(self) -> PipelineSurvivalModel:
        return PipelineSurvivalModel(self._load(SURVIVAL_MODEL))

    def _model_file(self, model: str) -> Path:
        return self.model_dir / f"{model}.joblib"

    def _save(self, model_object: object, model: str) -> None:
        self.model_dir.mkdir(parents=True, exist_ok=True)
        joblib.dump(model_object, self._model_file(model))

    def _load(self, model: str) -> Any:
        model_file = self._model_file(model)
        if not model_file.exists():
            raise ModelNotFoundError(model)
        return joblib.load(model_file)
