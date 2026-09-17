from pathlib import Path
from typing import Annotated, Literal

from fastapi import Depends, FastAPI, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field

from lib.chapter15.domain import ModelNotFoundError, Movie, Passenger
from lib.chapter15.infrastructure import JoblibModelStore
from lib.chapter15.service import PredictionService

MODEL_DIR = Path(__file__).resolve().parents[2] / "model"


class MovieRequest(BaseModel):
    sns1: float = Field(ge=0, description="公開後 1 か月の SNS 投稿数")
    sns2: float = Field(ge=0, description="公開後 2 か月の SNS 投稿数")
    actor: float = Field(ge=0, description="主演俳優の昨年のメディア露出度")
    original: Literal[0, 1] = Field(description="原作があれば 1")


class SalesResponse(BaseModel):
    sales: float


class PassengerRequest(BaseModel):
    pclass: Literal[1, 2, 3]
    sex: Literal["male", "female"]
    age: float | None = Field(default=None, ge=0)
    sib_sp: int = Field(ge=0, description="同乗した兄弟・配偶者の数")
    parch: int = Field(ge=0, description="同乗した親・子の数")
    fare: float = Field(ge=0)
    embarked: Literal["C", "Q", "S"] | None = None


class SurvivalResponse(BaseModel):
    survived: bool
    probability: float


class HealthResponse(BaseModel):
    status: Literal["ok", "degraded"]
    models: dict[str, bool]


def get_service() -> PredictionService:
    return PredictionService(JoblibModelStore(MODEL_DIR))


Service = Annotated[PredictionService, Depends(get_service)]

app = FastAPI(title="機械学習 API")


@app.exception_handler(ModelNotFoundError)
def model_not_found(request: Request, error: ModelNotFoundError) -> JSONResponse:
    return JSONResponse(status_code=503, content={"detail": str(error)})


@app.get("/health")
def health(service: Service) -> HealthResponse:
    models = service.health()
    return HealthResponse(
        status="ok" if all(models.values()) else "degraded", models=models
    )


@app.post("/cinema/sales")
def predict_sales(request: MovieRequest, service: Service) -> SalesResponse:
    prediction = service.predict_sales(Movie(**request.model_dump()))
    return SalesResponse(sales=prediction.sales)


@app.post("/survived")
def predict_survival(request: PassengerRequest, service: Service) -> SurvivalResponse:
    prediction = service.predict_survival(Passenger(**request.model_dump()))
    return SurvivalResponse(
        survived=prediction.survived, probability=prediction.probability
    )
