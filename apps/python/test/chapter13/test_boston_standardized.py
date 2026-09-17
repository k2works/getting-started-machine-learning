import pandas as pd
import pytest

from lib.chapter13.boston_standardized import standardize_boston


def boston_like() -> pd.DataFrame:
    return pd.DataFrame(
        {
            "CRIME": ["high", "low", "very_low", "low"],
            "RM": [5.0, 6.0, None, 7.0],
            "PRICE": [10.0, 20.0, 30.0, 40.0],
        }
    )


class TestStandardizeBoston:
    def test_CRIMEをダミー変数の列に置き換える(self) -> None:
        df = standardize_boston(boston_like())

        assert list(df.columns) == ["RM", "PRICE", "low", "very_low"]

    def test_欠損値を補完してから各列を平均0と標準偏差1にそろえる(self) -> None:
        df = standardize_boston(boston_like())

        assert df.isna().sum().sum() == 0
        assert df.mean().tolist() == pytest.approx([0.0] * 4)
        assert df.std(ddof=0).tolist() == pytest.approx([1.0] * 4)
