import pytest

from lib.dataset import data_dir


def requires_data(*file_names: str) -> pytest.MarkDecorator:
    """学習データが配置されていなければテストをスキップするマーカーを返す。"""
    missing = [name for name in file_names if not (data_dir() / name).exists()]
    return pytest.mark.skipif(
        bool(missing),
        reason=f"学習データ {', '.join(missing)} が配置されていない（gulp data:setup）",
    )
