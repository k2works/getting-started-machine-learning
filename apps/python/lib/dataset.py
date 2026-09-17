import os
from pathlib import Path

APPS_DIR = Path(__file__).resolve().parents[2]


def data_dir() -> Path:
    env = os.environ.get("ML_DATA_DIR")
    if env:
        return Path(env)
    return APPS_DIR / "data" / "sukkiri-ml"
