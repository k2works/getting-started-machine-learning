from collections.abc import Iterator

import pytest
from fastapi.testclient import TestClient

from lib.chapter15.api import MODEL_DIR, app, get_service
from lib.chapter15.infrastructure import JoblibModelStore
from lib.chapter15.service import PredictionService
from test.chapter15.stubs import EmptyModelStore, StubModelStore

MOVIE_JSON = {"sns1": 200.0, "sns2": 500.0, "actor": 3000.0, "original": 1}
PASSENGER_JSON = {
    "pclass": 1,
    "sex": "female",
    "age": None,
    "sib_sp": 0,
    "parch": 0,
    "fare": 50.0,
    "embarked": "C",
}


@pytest.fixture
def client() -> Iterator[TestClient]:
    app.dependency_overrides[get_service] = lambda: PredictionService(StubModelStore())
    yield TestClient(app)
    app.dependency_overrides.clear()


@pytest.fixture
def client_without_models() -> Iterator[TestClient]:
    app.dependency_overrides[get_service] = lambda: PredictionService(EmptyModelStore())
    yield TestClient(app)
    app.dependency_overrides.clear()


class TestCinemaSales:
    def test_映画の特徴量を送ると予測した興行収入を返す(
        self, client: TestClient
    ) -> None:
        response = client.post("/cinema/sales", json=MOVIE_JSON)

        assert response.status_code == 200
        assert response.json() == {"sales": 1200.0}

    @pytest.mark.parametrize(
        ("field", "value"),
        [("sns1", -1.0), ("actor", "多い"), ("original", 2)],
    )
    def test_特徴量が不正なら422を返す(
        self, client: TestClient, field: str, value: object
    ) -> None:
        response = client.post("/cinema/sales", json={**MOVIE_JSON, field: value})

        assert response.status_code == 422

    def test_モデルが無ければ503を返す(self, client_without_models: TestClient) -> None:
        response = client_without_models.post("/cinema/sales", json=MOVIE_JSON)

        assert response.status_code == 503
        assert response.json() == {"detail": "学習済みモデル cinema が見つかりません"}


class TestSurvived:
    def test_乗客の特徴量を送ると生存の予測と確率を返す(
        self, client: TestClient
    ) -> None:
        response = client.post("/survived", json=PASSENGER_JSON)

        assert response.status_code == 200
        assert response.json() == {"survived": True, "probability": 0.8}

    def test_年齢と乗船港は省略できる(self, client: TestClient) -> None:
        passenger = {
            key: value
            for key, value in PASSENGER_JSON.items()
            if key not in ("age", "embarked")
        }

        response = client.post("/survived", json=passenger)

        assert response.status_code == 200

    @pytest.mark.parametrize(
        ("field", "value"),
        [("pclass", 4), ("sex", "unknown"), ("embarked", "X"), ("fare", -5.0)],
    )
    def test_特徴量が不正なら422を返す(
        self, client: TestClient, field: str, value: object
    ) -> None:
        response = client.post("/survived", json={**PASSENGER_JSON, field: value})

        assert response.status_code == 422

    def test_モデルが無ければ503を返す(self, client_without_models: TestClient) -> None:
        response = client_without_models.post("/survived", json=PASSENGER_JSON)

        assert response.status_code == 503
        assert response.json() == {"detail": "学習済みモデル survived が見つかりません"}


class TestHealth:
    def test_すべてのモデルを読み込めればokを返す(self, client: TestClient) -> None:
        response = client.get("/health")

        assert response.status_code == 200
        assert response.json() == {
            "status": "ok",
            "models": {"cinema": True, "survived": True},
        }

    def test_読み込めないモデルがあればdegradedを返す(
        self, client_without_models: TestClient
    ) -> None:
        response = client_without_models.get("/health")

        assert response.status_code == 200
        assert response.json() == {
            "status": "degraded",
            "models": {"cinema": False, "survived": False},
        }


class TestGetService:
    def test_既定ではappsのmodelディレクトリのモデルを使う(self) -> None:
        service = get_service()

        assert isinstance(service.store, JoblibModelStore)
        assert service.store.model_dir == MODEL_DIR
        assert MODEL_DIR.parts[-3:] == ("apps", "python", "model")
