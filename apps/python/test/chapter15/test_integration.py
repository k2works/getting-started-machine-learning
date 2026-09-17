from collections.abc import Iterator
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

from lib.chapter15.__main__ import main
from lib.chapter15.api import app, get_service
from lib.chapter15.infrastructure import JoblibModelStore
from lib.chapter15.service import PredictionService
from lib.chapter15.training import train_and_save_models
from lib.dataset import data_dir
from test.markers import requires_data


@pytest.fixture(scope="module")
def model_dir(tmp_path_factory: pytest.TempPathFactory) -> Path:
    directory = tmp_path_factory.mktemp("model")
    train_and_save_models(data_dir(), JoblibModelStore(directory))
    return directory


@pytest.fixture
def client(model_dir: Path) -> Iterator[TestClient]:
    store = JoblibModelStore(model_dir)
    app.dependency_overrides[get_service] = lambda: PredictionService(store)
    yield TestClient(app)
    app.dependency_overrides.clear()


@requires_data("cinema.csv", "Survived.csv")
class TestTrainedModels:
    def test_学習したモデルを保存するとヘルスチェックがokになる(
        self, client: TestClient
    ) -> None:
        response = client.get("/health")

        assert response.json()["status"] == "ok"

    def test_学習した線形回帰モデルで興行収入を予測する(
        self, client: TestClient
    ) -> None:
        movie = {"sns1": 200.0, "sns2": 500.0, "actor": 3000.0, "original": 1}

        response = client.post("/cinema/sales", json=movie)

        assert response.status_code == 200
        assert response.json()["sales"] == pytest.approx(7895.31, abs=0.01)

    def test_学習したパイプラインで乗客の生存を予測する(
        self, client: TestClient
    ) -> None:
        passenger = {
            "pclass": 1,
            "sex": "female",
            "sib_sp": 0,
            "parch": 0,
            "fare": 50.0,
        }

        response = client.post("/survived", json=passenger)

        assert response.status_code == 200
        assert response.json()["survived"] is True

    def test_学習したパイプラインで死亡と生存確率を予測する(
        self, client: TestClient
    ) -> None:
        passenger = {
            "pclass": 3,
            "sex": "male",
            "sib_sp": 0,
            "parch": 0,
            "fare": 8.0,
            "embarked": "S",
        }

        response = client.post("/survived", json=passenger)

        assert response.json() == {
            "survived": False,
            "probability": pytest.approx(0.1622, abs=0.0001),
        }


@requires_data("cinema.csv", "Survived.csv")
class TestMain:
    def test_実行するとモデルを保存して起動方法を表示する(
        self, tmp_path: Path, capsys: pytest.CaptureFixture[str]
    ) -> None:
        main(model_dir=tmp_path)

        assert (tmp_path / "cinema.joblib").exists()
        assert (tmp_path / "survived.joblib").exists()
        assert capsys.readouterr().out == (
            "学習済みモデルを保存しました: cinema.joblib, survived.joblib\n"
            "API の起動: uv run uvicorn lib.chapter15.api:app --port 8015\n"
        )
