from pathlib import Path

from lib.chapter15.api import MODEL_DIR
from lib.chapter15.infrastructure import (
    SALES_MODEL,
    SURVIVAL_MODEL,
    JoblibModelStore,
)
from lib.chapter15.training import train_and_save_models
from lib.dataset import data_dir

PORT = 8015


def main(model_dir: Path = MODEL_DIR) -> None:
    train_and_save_models(data_dir(), JoblibModelStore(model_dir))
    print(
        f"学習済みモデルを保存しました: {SALES_MODEL}.joblib, {SURVIVAL_MODEL}.joblib"
    )
    print(f"API の起動: uv run uvicorn lib.chapter15.api:app --port {PORT}")


if __name__ == "__main__":
    main()
