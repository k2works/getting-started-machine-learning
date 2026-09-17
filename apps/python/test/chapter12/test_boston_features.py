from pathlib import Path

import pandas as pd
import pytest

from lib.chapter12.boston_features import (
    feature_names,
    fit_polynomial_scaler,
    prepare_boston,
    remove_outliers,
    transform,
)


class TestPolynomialScaler:
    def test_訓練データで標準化してから2乗の列を加える(self) -> None:
        x = pd.DataFrame({"RM": [1.0, 2.0, 3.0]})

        scaler = fit_polynomial_scaler(x)

        z = 1.224744871391589
        assert transform(scaler, x).tolist() == [
            pytest.approx([-z, z**2]),
            pytest.approx([0.0, 0.0]),
            pytest.approx([z, z**2]),
        ]

    def test_テストデータも訓練データの平均値と標準偏差で標準化する(self) -> None:
        train = pd.DataFrame({"RM": [1.0, 2.0, 3.0]})
        test = pd.DataFrame({"RM": [2.0]})

        scaler = fit_polynomial_scaler(train)

        assert transform(scaler, test).tolist() == [pytest.approx([0.0, 0.0])]

    def test_2つの特徴量から2乗と交互作用の列を作り名前を付ける(self) -> None:
        x = pd.DataFrame({"RM": [1.0, 2.0, 3.0], "LSTAT": [3.0, 1.0, 2.0]})

        scaler = fit_polynomial_scaler(x)

        assert feature_names(scaler) == ["RM", "LSTAT", "RM^2", "RM LSTAT", "LSTAT^2"]
        assert transform(scaler, x).shape == (3, 5)


class TestRemoveOutliers:
    def test_平均から標準偏差の3倍より離れた値を持つ行を除く(self) -> None:
        df = pd.DataFrame({"RM": [1.0] * 11 + [100.0]})

        assert remove_outliers(df, ["RM"], threshold=3.0)["RM"].tolist() == [1.0] * 11

    def test_外れ値が無ければすべての行を残す(self) -> None:
        df = pd.DataFrame({"RM": [5.0, 6.0, 7.0]})

        assert len(remove_outliers(df, ["RM"], threshold=3.0)) == 3

    def test_指定した列の値だけで外れ値を判定する(self) -> None:
        df = pd.DataFrame({"RM": [1.0] * 12, "ZN": [0.0] * 11 + [100.0]})

        assert len(remove_outliers(df, ["RM"], threshold=3.0)) == 12


class TestPrepareBoston:
    def test_訓練データと検証データとテストデータに分けて多項式特徴量を作る(
        self, tmp_path: Path
    ) -> None:
        rows = [f"low,6.{i},1{i}.5,{i + 3}.2,2{i}.0" for i in range(10)]
        csv_file = tmp_path / "boston.csv"
        csv_file.write_text("CRIME,RM,PTRATIO,LSTAT,PRICE\n" + "\n".join(rows) + "\n")

        dataset = prepare_boston(csv_file, test_size=0.3, validation_size=0.3, seed=0)

        assert dataset.x_train.shape == (4, 9)
        assert dataset.x_valid.shape == (3, 9)
        assert dataset.x_test.shape == (3, 9)
        assert (len(dataset.t_train), len(dataset.t_valid), len(dataset.t_test)) == (
            4,
            3,
            3,
        )
        assert dataset.feature_names[:3] == ["RM", "PTRATIO", "LSTAT"]
