from pathlib import Path

import pytest

from lib.dataset import data_dir


class TestDataDir:
    def test_環境変数ML_DATA_DIRが指定されていればそのディレクトリを返す(
        self, monkeypatch: pytest.MonkeyPatch, tmp_path: Path
    ) -> None:
        monkeypatch.setenv("ML_DATA_DIR", str(tmp_path))

        assert data_dir() == tmp_path

    def test_環境変数が無ければappsのdataディレクトリを返す(
        self, monkeypatch: pytest.MonkeyPatch
    ) -> None:
        monkeypatch.delenv("ML_DATA_DIR", raising=False)

        apps_dir = Path(__file__).resolve().parents[2]
        assert data_dir() == apps_dir / "data" / "sukkiri-ml"
