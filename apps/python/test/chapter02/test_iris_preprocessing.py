from pathlib import Path

import pandas as pd
import pytest
from sklearn.model_selection import train_test_split

from lib.chapter02.__main__ import main
from lib.chapter02.iris_preprocessing import (
    column_means,
    count_missing,
    fill_missing,
    load_iris,
    prepare_iris,
    split_features_and_target,
    split_train_test,
)
from lib.dataset import data_dir
from test.markers import requires_data

HEADER = "がく片長さ,がく片幅,花弁長さ,花弁幅,種類\n"


def write_csv(tmp_path: Path, rows: str) -> Path:
    csv_file = tmp_path / "iris.csv"
    csv_file.write_text(HEADER + rows, encoding="utf-8-sig")
    return csv_file


class TestLoadIris:
    def test_BOM付きCSVを読み込むと列名にBOMが残らない(self, tmp_path: Path) -> None:
        csv_file = write_csv(tmp_path, "0.1,0.2,0.3,0.4,Iris-setosa\n")

        df = load_iris(csv_file)

        assert list(df.columns) == [
            "がく片長さ",
            "がく片幅",
            "花弁長さ",
            "花弁幅",
            "種類",
        ]

    def test_空欄は欠損値として読み込む(self, tmp_path: Path) -> None:
        csv_file = write_csv(tmp_path, "0.1,,0.3,0.4,Iris-setosa\n")

        df = load_iris(csv_file)

        assert pd.isna(df.loc[0, "がく片幅"])


class TestCountMissing:
    def test_列ごとの欠損値の数を数える(self) -> None:
        df = pd.DataFrame(
            {
                "がく片長さ": [0.1, None, None],
                "がく片幅": [0.2, 0.3, None],
                "種類": ["Iris-setosa", "Iris-setosa", "Iris-virginica"],
            }
        )

        assert count_missing(df).to_dict() == {
            "がく片長さ": 2,
            "がく片幅": 1,
            "種類": 0,
        }


class TestColumnMeans:
    def test_欠損値を除いて列ごとの平均値を求める(self) -> None:
        df = pd.DataFrame({"がく片長さ": [0.1, None, 0.3], "がく片幅": [0.2, 0.4, 0.9]})

        means = column_means(df, ["がく片長さ", "がく片幅"])

        assert means == pytest.approx({"がく片長さ": 0.2, "がく片幅": 0.5})


class TestFillMissing:
    def test_欠損値を列ごとに指定した値で補完する(self) -> None:
        df = pd.DataFrame({"がく片長さ": [0.1, None], "がく片幅": [None, 0.4]})

        filled = fill_missing(df, {"がく片長さ": 0.2, "がく片幅": 0.5})

        assert filled.to_dict(orient="list") == {
            "がく片長さ": [0.1, 0.2],
            "がく片幅": [0.5, 0.4],
        }

    def test_元のデータフレームは変更しない(self) -> None:
        df = pd.DataFrame({"がく片長さ": [0.1, None]})

        fill_missing(df, {"がく片長さ": 0.2})

        assert count_missing(df)["がく片長さ"] == 1


class TestSplitFeaturesAndTarget:
    def test_特徴量の列と正解ラベルの列に分ける(self) -> None:
        df = pd.DataFrame(
            {
                "がく片長さ": [0.1, 0.5],
                "花弁幅": [0.4, 0.8],
                "種類": ["Iris-setosa", "Iris-virginica"],
            }
        )

        x, t = split_features_and_target(df, "種類")

        assert list(x.columns) == ["がく片長さ", "花弁幅"]
        assert t.to_list() == ["Iris-setosa", "Iris-virginica"]


def numbered_dataset(size: int) -> tuple[pd.DataFrame, pd.Series]:
    x = pd.DataFrame({"x": range(size)})
    t = pd.Series([f"label{i}" for i in range(size)])
    return x, t


class TestSplitTrainTest:
    def test_テストデータの割合どおりの件数に分ける(self) -> None:
        x, t = numbered_dataset(10)

        split = split_train_test(x, t, test_size=0.3, seed=0)

        assert (len(split.x_train), len(split.x_test)) == (7, 3)
        assert (len(split.t_train), len(split.t_test)) == (7, 3)

    def test_件数が変わってもテストデータの割合どおりに分ける(self) -> None:
        x, t = numbered_dataset(20)

        split = split_train_test(x, t, test_size=0.25, seed=0)

        assert (len(split.x_train), len(split.x_test)) == (15, 5)
        assert (len(split.t_train), len(split.t_test)) == (15, 5)

    def test_すべての行を重複なく訓練データとテストデータのどちらかに入れる(
        self,
    ) -> None:
        x, t = numbered_dataset(10)

        split = split_train_test(x, t, test_size=0.3, seed=0)

        train_rows = set(split.x_train["x"])
        test_rows = set(split.x_test["x"])
        assert train_rows | test_rows == set(range(10))
        assert train_rows & test_rows == set()

    def test_特徴量と正解ラベルの対応を保ったまま分ける(self) -> None:
        x, t = numbered_dataset(10)

        split = split_train_test(x, t, test_size=0.3, seed=0)

        assert split.x_train.index.equals(split.t_train.index)
        assert split.x_test.index.equals(split.t_test.index)

    def test_同じシードなら同じ分け方になる(self) -> None:
        x, t = numbered_dataset(10)

        first = split_train_test(x, t, test_size=0.3, seed=42)
        second = split_train_test(x, t, test_size=0.3, seed=42)

        assert first.x_test.index.equals(second.x_test.index)

    def test_シードが違えば違う分け方になる(self) -> None:
        x, t = numbered_dataset(10)

        first = split_train_test(x, t, test_size=0.3, seed=0)
        second = split_train_test(x, t, test_size=0.3, seed=1)

        assert not first.x_test.index.equals(second.x_test.index)

    def test_scikit_learnのtrain_test_splitと同じ件数に分ける(self) -> None:
        x, t = numbered_dataset(150)

        split = split_train_test(x, t, test_size=0.3, seed=0)
        x_train, x_test = train_test_split(x, test_size=0.3, random_state=0)

        assert (len(split.x_train), len(split.x_test)) == (len(x_train), len(x_test))


class TestPrepareIris:
    def test_訓練データとテストデータのどちらにも欠損値が残らない(
        self, tmp_path: Path
    ) -> None:
        csv_file = write_csv(
            tmp_path,
            "0.1,,0.3,0.4,Iris-setosa\n"
            "0.2,0.3,,0.5,Iris-setosa\n"
            ",0.4,0.5,0.6,Iris-virginica\n"
            "0.4,0.5,0.6,,Iris-virginica\n",
        )

        split = prepare_iris(csv_file, test_size=0.5, seed=0)

        assert count_missing(split.x_train).sum() == 0
        assert count_missing(split.x_test).sum() == 0


@requires_data("iris.csv")
class TestIrisData:
    def test_実データの列ごとの欠損値の数を数える(self) -> None:
        df = load_iris(data_dir() / "iris.csv")

        assert count_missing(df).to_dict() == {
            "がく片長さ": 2,
            "がく片幅": 1,
            "花弁長さ": 2,
            "花弁幅": 2,
            "種類": 0,
        }

    def test_実データを105件と45件に分けて欠損値を補完する(self) -> None:
        split = prepare_iris(data_dir() / "iris.csv", test_size=0.3, seed=0)

        assert (len(split.x_train), len(split.x_test)) == (105, 45)
        assert count_missing(split.x_train).sum() == 0
        assert count_missing(split.x_test).sum() == 0

    def test_実行すると前処理の結果を表示する(
        self, capsys: pytest.CaptureFixture[str]
    ) -> None:
        main()

        assert capsys.readouterr().out == (
            "データ件数: 150\n"
            "欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0\n"
            "訓練データ: 105 件, テストデータ: 45 件\n"
            "補完後の欠損値の数: 訓練データ 0, テストデータ 0\n"
        )
