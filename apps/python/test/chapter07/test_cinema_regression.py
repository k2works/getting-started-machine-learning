import math
from pathlib import Path

import numpy as np
import pandas as pd
import pytest
from sklearn import metrics
from sklearn.linear_model import LinearRegression

from lib.chapter07.__main__ import main
from lib.chapter07.cinema_regression import (
    LinearModel,
    fit_linear_regression,
    load_cinema,
    mean_absolute_error,
    prepare_cinema,
    r2_score,
    remove_outliers,
    root_mean_squared_error,
)
from lib.dataset import data_dir
from test.markers import requires_data

HEADER = "cinema_id,SNS1,SNS2,actor,original,sales\n"


def write_csv(tmp_path: Path, rows: str) -> Path:
    csv_file = tmp_path / "cinema.csv"
    csv_file.write_text(HEADER + rows, encoding="utf-8")
    return csv_file


class TestLoadCinema:
    def test_CSVを読み込み空欄を欠損値にする(self, tmp_path: Path) -> None:
        csv_file = write_csv(tmp_path, "1,,500,9000.5,1,9500\n")

        df = load_cinema(csv_file)

        assert list(df.columns) == [
            "cinema_id",
            "SNS1",
            "SNS2",
            "actor",
            "original",
            "sales",
        ]
        assert pd.isna(df.loc[0, "SNS1"])


class TestRemoveOutliers:
    def test_SNS2が1000を超え売上が8500未満の行を取り除く(self) -> None:
        df = pd.DataFrame({"SNS2": [1200, 600], "sales": [8000, 9500]})

        assert remove_outliers(df)["SNS2"].to_list() == [600]

    def test_条件の片方だけを満たす行は残す(self) -> None:
        df = pd.DataFrame({"SNS2": [1200, 600], "sales": [9800, 8000]})

        assert remove_outliers(df)["SNS2"].to_list() == [1200, 600]


class TestFitLinearRegression:
    def test_直線上の点から切片と係数を求める(self) -> None:
        x = pd.DataFrame({"x": [0.0, 1.0, 2.0, 3.0]})
        t = pd.Series([1.0, 3.0, 5.0, 7.0])

        model = fit_linear_regression(x, t)

        assert model.intercept == pytest.approx(1.0)
        assert model.coefficients == pytest.approx({"x": 2.0})

    def test_複数の特徴量から切片と係数を求める(self) -> None:
        x = pd.DataFrame(
            {"a": [0.0, 1.0, 0.0, 2.0, 1.0], "b": [0.0, 0.0, 1.0, 1.0, 3.0]}
        )
        t = 3.0 * x["a"] - 2.0 * x["b"] + 5.0

        model = fit_linear_regression(x, t)

        assert model.intercept == pytest.approx(5.0)
        assert model.coefficients == pytest.approx({"a": 3.0, "b": -2.0})


class TestPredict:
    def test_切片と係数から予測値を計算する(self) -> None:
        model = LinearModel(intercept=1.0, coefficients={"a": 2.0, "b": -1.0})
        x = pd.DataFrame({"a": [1.0, 3.0], "b": [4.0, 0.5]})

        assert model.predict(x).tolist() == pytest.approx([-1.0, 6.5])

    def test_列の並び順が違っても列名で係数を対応させる(self) -> None:
        model = LinearModel(intercept=1.0, coefficients={"a": 2.0, "b": -1.0})
        x = pd.DataFrame({"b": [4.0, 0.5], "a": [1.0, 3.0]})

        assert model.predict(x).tolist() == pytest.approx([-1.0, 6.5])


class TestMeanAbsoluteError:
    def test_誤差の絶対値の平均を求める(self) -> None:
        t = pd.Series([3.0, 5.0, 7.0])

        assert mean_absolute_error(t, np.array([2.0, 5.0, 9.0])) == pytest.approx(1.0)

    def test_予測が大きく外れるほど値が大きくなる(self) -> None:
        t = pd.Series([3.0, 5.0, 7.0])

        assert mean_absolute_error(t, np.array([1.0, 8.0, 7.0])) == pytest.approx(5 / 3)


class TestRootMeanSquaredError:
    def test_誤差の2乗の平均の平方根を求める(self) -> None:
        t = pd.Series([3.0, 5.0, 7.0])

        assert root_mean_squared_error(t, np.array([2.0, 5.0, 9.0])) == pytest.approx(
            math.sqrt(5 / 3)
        )


class TestR2Score:
    def test_予測がすべて正解なら1になる(self) -> None:
        t = pd.Series([3.0, 5.0, 7.0])

        assert r2_score(t, np.array([3.0, 5.0, 7.0])) == pytest.approx(1.0)

    def test_平均値を予測し続けるモデルより良い分だけ1に近づく(self) -> None:
        t = pd.Series([3.0, 5.0, 7.0])

        assert r2_score(t, np.array([2.0, 5.0, 9.0])) == pytest.approx(1 - 5 / 8)


def noisy_dataset() -> tuple[pd.DataFrame, pd.Series]:
    rng = np.random.default_rng(0)
    x = pd.DataFrame(rng.uniform(0, 10, size=(30, 3)), columns=["a", "b", "c"])
    noise = rng.normal(0, 1, size=30)
    t = pd.Series(4.0 + 1.5 * x["a"] - 0.5 * x["b"] + 2.0 * x["c"] + noise)
    return x, t


class TestCompareWithScikitLearn:
    def test_LinearRegressionと同じ切片と係数になる(self) -> None:
        x, t = noisy_dataset()

        model = fit_linear_regression(x, t)
        expected = LinearRegression().fit(x, t)

        assert model.intercept == pytest.approx(expected.intercept_)
        assert list(model.coefficients.values()) == pytest.approx(expected.coef_)

    def test_評価指標がscikit_learnと一致する(self) -> None:
        x, t = noisy_dataset()
        y = fit_linear_regression(x, t).predict(x)

        assert mean_absolute_error(t, y) == pytest.approx(
            metrics.mean_absolute_error(t, y)
        )
        assert root_mean_squared_error(t, y) == pytest.approx(
            metrics.root_mean_squared_error(t, y)
        )
        assert r2_score(t, y) == pytest.approx(metrics.r2_score(t, y))


class TestPrepareCinema:
    def test_外れ値を除き特徴量を選んで分割し欠損値を補完する(
        self, tmp_path: Path
    ) -> None:
        csv_file = write_csv(
            tmp_path,
            "1,100,300,9000.0,0,9200\n"
            "2,,400,9500.0,1,9800\n"
            "3,300,500,,1,10100\n"
            "4,150,1200,8800.0,0,8100\n"
            "5,250,700,9900.0,1,10300\n"
            "6,120,650,9100.0,0,9400\n",
        )

        split = prepare_cinema(csv_file, test_size=0.4, seed=0)

        assert list(split.x_train.columns) == ["SNS1", "SNS2", "actor", "original"]
        assert (len(split.x_train), len(split.x_test)) == (3, 2)
        assert 8100 not in set(split.t_train) | set(split.t_test)
        assert split.x_train.isna().sum().sum() == 0
        assert split.x_test.isna().sum().sum() == 0


@requires_data("cinema.csv")
class TestCinemaData:
    def test_実データから外れ値を1件取り除く(self) -> None:
        df = load_cinema(data_dir() / "cinema.csv")

        assert (len(df), len(remove_outliers(df))) == (100, 99)

    def test_実データで自作のモデルとLinearRegressionのR2が一致する(self) -> None:
        split = prepare_cinema(data_dir() / "cinema.csv", test_size=0.2, seed=0)

        model = fit_linear_regression(split.x_train, split.t_train)
        expected = LinearRegression().fit(split.x_train, split.t_train)

        assert r2_score(split.t_test, model.predict(split.x_test)) == pytest.approx(
            expected.score(split.x_test, split.t_test)
        )

    def test_実行すると学習した係数と評価指標を表示する(
        self, capsys: pytest.CaptureFixture[str]
    ) -> None:
        main()

        assert capsys.readouterr().out == (
            "データ件数: 100\n"
            "外れ値を除いた件数: 99\n"
            "訓練データ: 79 件, テストデータ: 20 件\n"
            "切片: 6323.61\n"
            "係数: SNS1=1.2576, SNS2=0.4736, actor=0.2728, original=264.9150\n"
            "テストデータの評価: R2=0.6811, MAE=311.63, RMSE=367.57\n"
        )
