from pathlib import Path

import pandas as pd
import pytest
from sklearn.preprocessing import PolynomialFeatures, StandardScaler

from lib.chapter02.iris_preprocessing import TrainTestSplit
from lib.chapter09.__main__ import COLUMNS, SQUARES, main
from lib.chapter09.feature_engineering import (
    Standardizer,
    dummy_categories,
    encode_dummies,
    iqr_outliers,
    join_weather,
    load_bike,
    load_weather,
    mean_count_by_weather,
    polynomial_features,
    prepare_boston,
    remove_target_outliers,
    score_feature_set,
)
from lib.dataset import data_dir
from test.markers import requires_data


class TestDummyCategories:
    def test_先頭を除いたカテゴリを辞書順に返す(self) -> None:
        crime = pd.Series(["low", "high", "very_low", "low"])

        assert dummy_categories(crime) == ["low", "very_low"]


class TestEncodeDummies:
    def test_カテゴリごとに0と1の列を作り元の列を取り除く(self) -> None:
        df = pd.DataFrame({"CRIME": ["low", "high", "very_low"], "RM": [6.1, 5.2, 7.3]})

        encoded = encode_dummies(df, "CRIME", ["low", "very_low"])

        assert encoded.to_dict(orient="list") == {
            "RM": [6.1, 5.2, 7.3],
            "CRIME_low": [1, 0, 0],
            "CRIME_very_low": [0, 0, 1],
        }

    def test_カテゴリに無い値はすべての列が0になる(self) -> None:
        df = pd.DataFrame({"CRIME": ["unknown"]})

        encoded = encode_dummies(df, "CRIME", ["low", "very_low"])

        assert encoded.to_dict(orient="list") == {
            "CRIME_low": [0],
            "CRIME_very_low": [0],
        }

    def test_pandasのget_dummiesと同じ結果になる(self) -> None:
        df = pd.DataFrame({"CRIME": ["low", "high", "very_low", "low"]})

        encoded = encode_dummies(df, "CRIME", dummy_categories(df["CRIME"]))
        expected = pd.get_dummies(df, columns=["CRIME"], drop_first=True, dtype=int)

        pd.testing.assert_frame_equal(encoded, expected)


class TestStandardizer:
    def test_訓練データから列ごとの平均と標準偏差を求める(self) -> None:
        df = pd.DataFrame({"RM": [1.0, 2.0, 3.0], "LSTAT": [10.0, 10.0, 40.0]})

        standardizer = Standardizer.fit(df)

        assert standardizer.means == pytest.approx({"RM": 2.0, "LSTAT": 20.0})
        assert standardizer.stds == pytest.approx(
            {"RM": (2 / 3) ** 0.5, "LSTAT": 200**0.5}
        )

    def test_訓練データの平均と標準偏差で別のデータを標準化する(self) -> None:
        standardizer = Standardizer(means={"RM": 2.0}, stds={"RM": 0.5})
        other = pd.DataFrame({"RM": [1.0, 2.0, 4.0]})

        standardized = standardizer.transform(other)

        assert standardized["RM"].to_list() == pytest.approx([-2.0, 0.0, 4.0])

    def test_すべて同じ値の列は0にする(self) -> None:
        df = pd.DataFrame({"CHAS": [1.0, 1.0, 1.0]})

        standardized = Standardizer.fit(df).transform(df)

        assert standardized["CHAS"].to_list() == [0.0, 0.0, 0.0]

    def test_scikit_learnのStandardScalerと同じ値になる(self) -> None:
        train = pd.DataFrame(
            {"RM": [5.5, 6.0, 7.5, 6.5], "LSTAT": [12.0, 4.0, 9.0, 3.0]}
        )
        test = pd.DataFrame({"RM": [6.2, 8.0], "LSTAT": [15.0, 2.0]})

        standardized = Standardizer.fit(train).transform(test)
        expected = StandardScaler().fit(train).transform(test)

        assert standardized.to_numpy() == pytest.approx(expected)


class TestPolynomialFeatures:
    def test_1列なら元の列と2乗の列を返す(self) -> None:
        df = pd.DataFrame({"RM": [2.0, 3.0]})

        features = polynomial_features(df, ["RM"])

        assert features.to_dict(orient="list") == {"RM": [2.0, 3.0], "RM^2": [4.0, 9.0]}

    def test_2列なら2乗の列と2つの列の積の列を加える(self) -> None:
        df = pd.DataFrame({"RM": [2.0, 3.0], "LSTAT": [5.0, 7.0]})

        features = polynomial_features(df, ["RM", "LSTAT"])

        assert list(features.columns) == ["RM", "LSTAT", "RM^2", "RM LSTAT", "LSTAT^2"]
        assert features.to_dict(orient="list") == {
            "RM": [2.0, 3.0],
            "LSTAT": [5.0, 7.0],
            "RM^2": [4.0, 9.0],
            "RM LSTAT": [10.0, 21.0],
            "LSTAT^2": [25.0, 49.0],
        }

    def test_scikit_learnのPolynomialFeaturesと同じ列名と値になる(self) -> None:
        df = pd.DataFrame(
            {
                "RM": [5.5, 6.0, 7.5],
                "LSTAT": [12.0, 4.0, 9.0],
                "PTRATIO": [18.0, 15.0, 20.0],
            }
        )
        columns = ["RM", "LSTAT", "PTRATIO"]

        features = polynomial_features(df, columns)
        expected = PolynomialFeatures(degree=2, include_bias=False).fit(df[columns])

        assert list(features.columns) == list(expected.get_feature_names_out())
        assert features.to_numpy() == pytest.approx(expected.transform(df[columns]))


class TestIqrOutliers:
    def test_第3四分位数からIQRの15倍より大きい値を外れ値とする(self) -> None:
        values = pd.Series([1.0, 2.0, 3.0, 4.0, 100.0])

        assert iqr_outliers(values).to_list() == [False, False, False, False, True]

    def test_第1四分位数からIQRの15倍より小さい値も外れ値とする(self) -> None:
        values = pd.Series([-100.0, 1.0, 2.0, 3.0, 4.0])

        assert iqr_outliers(values).to_list() == [True, False, False, False, False]


class TestLoadBikeAndWeather:
    def test_タブ区切りのファイルを読み込む(self, tmp_path: Path) -> None:
        tsv_file = tmp_path / "bike.tsv"
        tsv_file.write_text(
            "dteday\tweather_id\tcnt\n2030-04-01\t1\t120\n", encoding="utf-8"
        )

        bike = load_bike(tsv_file)

        assert bike.to_dict(orient="list") == {
            "dteday": ["2030-04-01"],
            "weather_id": [1],
            "cnt": [120],
        }

    def test_Shift_JISのファイルを読み込む(self, tmp_path: Path) -> None:
        csv_file = tmp_path / "weather.csv"
        csv_file.write_text("weather_id,weather\n1,晴れ\n", encoding="shift_jis")

        weather = load_weather(csv_file)

        assert weather.to_dict(orient="list") == {
            "weather_id": [1],
            "weather": ["晴れ"],
        }


class TestJoinWeather:
    def test_天気IDで天気の名前を結合する(self) -> None:
        bike = pd.DataFrame({"weather_id": [2, 1], "cnt": [80, 120]})
        weather = pd.DataFrame({"weather_id": [1, 2], "weather": ["晴れ", "曇り"]})

        joined = join_weather(bike, weather)

        assert joined.to_dict(orient="list") == {
            "weather_id": [2, 1],
            "cnt": [80, 120],
            "weather": ["曇り", "晴れ"],
        }

    def test_天気の表に無い天気IDの行は残さない(self) -> None:
        bike = pd.DataFrame({"weather_id": [1, 9], "cnt": [120, 30]})
        weather = pd.DataFrame({"weather_id": [1], "weather": ["晴れ"]})

        joined = join_weather(bike, weather)

        assert joined["cnt"].to_list() == [120]


class TestMeanCountByWeather:
    def test_天気ごとの平均利用者数を求める(self) -> None:
        joined = pd.DataFrame(
            {"weather": ["晴れ", "雨", "晴れ"], "cnt": [100, 20, 140]}
        )

        assert mean_count_by_weather(joined).to_dict() == {"晴れ": 120.0, "雨": 20.0}


BOSTON_HEADER = "CRIME,RM,NOX,PRICE\n"


class TestPrepareBoston:
    def test_ダミー変数化と欠損値の補完をして特徴量と価格に分ける(
        self, tmp_path: Path
    ) -> None:
        csv_file = tmp_path / "boston.csv"
        csv_file.write_text(
            BOSTON_HEADER
            + "low,6.0,,20.0\n"
            + "high,5.0,0.5,15.0\n"
            + "very_low,7.0,0.4,30.0\n"
            + "low,6.5,0.6,25.0\n",
            encoding="utf-8",
        )

        split = prepare_boston(csv_file, test_size=0.5, seed=0)

        assert list(split.x_train.columns) == [
            "RM",
            "NOX",
            "CRIME_low",
            "CRIME_very_low",
        ]
        assert list(split.x_test.columns) == [
            "RM",
            "NOX",
            "CRIME_low",
            "CRIME_very_low",
        ]
        assert split.x_train.isna().sum().sum() == 0
        assert split.x_test.isna().sum().sum() == 0
        assert (len(split.t_train), len(split.t_test)) == (2, 2)


def quadratic_split() -> TrainTestSplit:
    def price(rm: pd.Series) -> pd.Series:
        return 3 * rm**2 + 1

    x_train = pd.DataFrame({"RM": [1.0, 2.0, 3.0, 4.0], "LSTAT": [9.0, 7.0, 8.0, 6.0]})
    x_test = pd.DataFrame({"RM": [5.0, 6.0], "LSTAT": [5.0, 4.0]})
    return TrainTestSplit(
        x_train=x_train,
        x_test=x_test,
        t_train=price(x_train["RM"]),
        t_test=price(x_test["RM"]),
    )


class TestScoreFeatureSet:
    def test_2乗の項が無いと2次式の価格を当てきれない(self) -> None:
        train_score, _ = score_feature_set(quadratic_split(), ["RM"], ["RM"])

        assert train_score < 1.0

    def test_2乗の項を加えると2次式の価格を当てられる(self) -> None:
        scores = score_feature_set(quadratic_split(), ["RM"], ["RM", "RM^2"])

        assert scores == pytest.approx((1.0, 1.0))


class TestRemoveTargetOutliers:
    def test_訓練データから価格が外れ値の行を取り除きテストデータは残す(self) -> None:
        split = TrainTestSplit(
            x_train=pd.DataFrame({"RM": [5.0, 6.0, 6.5, 7.0, 8.0]}),
            x_test=pd.DataFrame({"RM": [9.0]}),
            t_train=pd.Series([1.0, 2.0, 3.0, 4.0, 100.0]),
            t_test=pd.Series([500.0]),
        )

        removed = remove_target_outliers(split)

        assert removed.x_train["RM"].to_list() == [5.0, 6.0, 6.5, 7.0]
        assert removed.t_train.to_list() == [1.0, 2.0, 3.0, 4.0]
        assert removed.t_test.to_list() == [500.0]


@requires_data("Boston.csv", "bike.tsv", "weather.csv")
class TestFeatureEngineeringData:
    def test_実データを70件と30件に分けて欠損値を補完する(self) -> None:
        split = prepare_boston(data_dir() / "Boston.csv", test_size=0.3, seed=0)

        assert (len(split.x_train), len(split.x_test)) == (70, 30)
        assert split.x_train.isna().sum().sum() == 0
        assert split.x_test.isna().sum().sum() == 0

    def test_2乗の項を加えるとテストデータの決定係数が上がる(self) -> None:
        split = prepare_boston(data_dir() / "Boston.csv", test_size=0.3, seed=0)

        _, base = score_feature_set(split, COLUMNS, COLUMNS)
        _, squares = score_feature_set(split, COLUMNS, COLUMNS + SQUARES)

        assert (base, squares) == pytest.approx((0.5848, 0.7283), abs=1e-4)

    def test_天気ごとの平均利用者数を求める(self) -> None:
        joined = join_weather(
            load_bike(data_dir() / "bike.tsv"), load_weather(data_dir() / "weather.csv")
        )

        means = mean_count_by_weather(joined)

        assert len(joined) == 731
        assert means.round(1).to_dict() == {
            "晴れ": 4876.8,
            "曇り": 4052.7,
            "雨": 1803.3,
        }

    def test_実行すると特徴量エンジニアリングの結果を表示する(
        self, capsys: pytest.CaptureFixture[str]
    ) -> None:
        main()

        assert capsys.readouterr().out == (
            "訓練データ: 70 件, テストデータ: 30 件\n"
            "特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, "
            "LSTAT, CRIME_low, CRIME_very_low\n"
            "標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00\n"
            "決定係数:\n"
            "  元の特徴量（3 列）: 訓練 0.6730, テスト 0.5848\n"
            "  2 乗の項を追加（6 列）: 訓練 0.8375, テスト 0.7283\n"
            "  交互作用の項も追加（9 列）: 訓練 0.8643, テスト 0.5799\n"
            "訓練データの PRICE の外れ値: 7 件\n"
            "  外れ値を除いて 2 乗の項を追加: 訓練 0.6443, テスト 0.6268\n"
            "天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3\n"
        )
