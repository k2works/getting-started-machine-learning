---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "iris データの欠損値を訓練データの平均値で補完し、シード付きの訓練・テストデータ分割を TDD で実装する。"
tags: [article,getting-start-ml,python]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T02:38:27Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

第 1 章では、欠損のないきれいなデータを読み込み、人間が書いたルールで判定しました。現実のデータには、空欄（欠損値）が混ざっています。欠損値が残ったままでは、多くの機械学習のアルゴリズムは学習できません。

この章では、アヤメ（iris）のデータを題材に、欠損値を補完し、データを **訓練データ** と **テストデータ** に分けるところまでを TDD で実装します。訓練・テストデータへの分割は、第 3 章以降のすべての章で使う土台になります。

この章から、データ処理のライブラリ [pandas](https://pandas.pydata.org/) を使います。第 1 章の標準ライブラリ `csv` との違いにも触れます。

## 2.2 題材とデータ

### iris.csv

`iris.csv` には、アヤメの花 150 件の測定値と品種が記録されています。

| 列 | 意味 | 値 |
|----|------|-----|
| がく片長さ | がく片の長さ | 小数（尺度を変換済み） |
| がく片幅 | がく片の幅 | 小数（尺度を変換済み） |
| 花弁長さ | 花弁の長さ | 小数（尺度を変換済み） |
| 花弁幅 | 花弁の幅 | 小数（尺度を変換済み） |
| 種類 | 品種 | `Iris-setosa`・`Iris-versicolor`・`Iris-virginica` が 50 件ずつ |

scikit-learn に同梱されている iris データとは値が異なります。特徴量の 4 列には合わせて 7 件の欠損値があります。

### なぜ訓練データとテストデータに分けるのか

モデルの良し悪しは、**学習に使っていないデータ** でどれだけ当たるかで測ります。学習に使ったデータで測ると、データを丸暗記しただけのモデルでも高い正解率が出てしまい、未知のデータに対する実力が分かりません。

そこで、手元のデータを訓練データ（学習に使う）とテストデータ（評価だけに使う）に分けます。この章では、150 件を 7 対 3 の 105 件と 45 件に分けます。

### 欠損値の補完でも訓練データとテストデータを混ぜない

欠損値を列の平均値で補完するとき、平均値は **訓練データだけ** から求め、その値で訓練データとテストデータの両方を補完します。データ全体の平均値を使うと、テストデータの情報が学習側に漏れてしまいます（データリーク）。この章の最後に作る `prepare_iris` は、この順序で前処理を行います。

## 2.3 開発環境の準備

pandas・NumPy・scikit-learn をプロジェクトに追加します。pandas の型チェック用に pandas-stubs も開発依存として追加します。

```bash
uv add pandas numpy scikit-learn
uv add --dev pandas-stubs
```

このリポジトリでは、後の章で使うライブラリもまとめて追加しています。選定の理由は [ADR 001](../../../adr/001-python-ml-libraries.md) を参照してください。

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] iris.csv を読み込む
  - [ ] BOM 付き CSV の列名に BOM が残らない
  - [ ] 空欄を欠損値として読み込む
- [ ] 列ごとの欠損値の数を数える
- [ ] 列ごとの平均値を求める
- [ ] 欠損値を補完する
  - [ ] 列ごとに指定した値で補完する
  - [ ] 元のデータは変更しない
- [ ] 特徴量と正解ラベルに分ける
- [ ] 訓練データとテストデータに分ける
  - [ ] テストデータの割合どおりの件数に分ける
  - [ ] すべての行を重複なくどちらかに入れる
  - [ ] 特徴量と正解ラベルの対応を保つ
  - [ ] 同じシードなら同じ分け方になる
  - [ ] シードが違えば違う分け方になる
- [ ] 訓練データの平均値で両方を補完する
- [ ] 実データで前処理の結果を表示する

## 2.5 pandas で読み込む

### 学習用テストで pandas の振る舞いを確かめる

第 1 章では、BOM を取り除くために `encoding="utf-8-sig"` を指定しました。pandas の `read_csv` はどうでしょうか。ドキュメントを読むより、テストで確かめるのが確実です。ライブラリの振る舞いを確かめるために書くテストを **学習用テスト** と呼びます。

> 学習用テスト
>
> 外部のソフトウェアのテストを書くべきだろうか——そのソフトウェアに対して新しいことを初めて行おうとした段階で書いてみよう。
>
> — テスト駆動開発

```python
# test/chapter02/test_iris_preprocessing.py
from pathlib import Path

import pandas as pd

from lib.chapter02.iris_preprocessing import count_missing, load_iris

HEADER = "がく片長さ,がく片幅,花弁長さ,花弁幅,種類\n"


def write_csv(tmp_path: Path, rows: str) -> Path:
    csv_file = tmp_path / "iris.csv"
    csv_file.write_text(HEADER + rows, encoding="utf-8-sig")
    return csv_file


class TestLoadIris:
    def test_BOM付きCSVを読み込むと列名にBOMが残らない(self, tmp_path: Path) -> None:
        csv_file = write_csv(tmp_path, "0.1,0.2,0.3,0.4,Iris-setosa\n")

        df = load_iris(csv_file)

        assert list(df.columns) == ["がく片長さ", "がく片幅", "花弁長さ", "花弁幅", "種類"]

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

        assert count_missing(df).to_dict() == {"がく片長さ": 2, "がく片幅": 1, "種類": 0}
```

`pd.DataFrame` は、列名をキー、列の値のリストを値とする辞書から作れる表形式のデータです。テストでは CSV を経由せず、必要な形のデータを直接作れます。

```bash
uv run pytest test/chapter02
```

```text
E   ModuleNotFoundError: No module named 'lib.chapter02.iris_preprocessing'
!!!!!!!!!!!!!!!!!!! Interrupted: 1 error during collection !!!!!!!!!!!!!!!!!!!!
============================== 1 error in 0.52s ===============================
```

### Green: 明白な実装

どちらも pandas の機能をそのまま使うだけなので、明白な実装で進めます。

```python
# lib/chapter02/iris_preprocessing.py
from pathlib import Path

import pandas as pd


def load_iris(csv_file: Path) -> pd.DataFrame:
    return pd.read_csv(csv_file)


def count_missing(df: pd.DataFrame) -> pd.Series:
    return df.isna().sum()
```

```text
test/chapter02/test_iris_preprocessing.py::TestLoadIris::test_BOM付きCSVを読み込むと列名にBOMが残らない PASSED [ 33%]
test/chapter02/test_iris_preprocessing.py::TestLoadIris::test_空欄は欠損値として読み込む PASSED [ 66%]
test/chapter02/test_iris_preprocessing.py::TestCountMissing::test_列ごとの欠損値の数を数える PASSED [100%]
============================== 3 passed in 0.39s ==============================
```

`encoding` を指定しなくても、pandas は BOM を取り除いて読み込むことが分かりました。空欄は `NaN`（欠損値）になり、`isna()` で欠損かどうかの真偽値の表に、`sum()` で列ごとの `True` の数に変換できます。

`csv` モジュールと pandas の違いをまとめます。

| 観点 | `csv` モジュール（第 1 章） | pandas（第 2 章） |
|------|--------------------------|------------------|
| BOM | `encoding="utf-8-sig"` の指定が必要 | 指定しなくても取り除く |
| 値の型 | すべて文字列。自分で `int` などに変換する | 列ごとに数値・文字列を推定する |
| 空欄 | 空文字列 `""` | 欠損値 `NaN` |
| 扱う単位 | 1 行ずつ | 列・表全体をまとめて |

## 2.6 平均値で欠損値を補完する

### 平均値を求める

欠損値を除いて、列ごとの平均値を求めます。

```python
class TestColumnMeans:
    def test_欠損値を除いて列ごとの平均値を求める(self) -> None:
        df = pd.DataFrame({"がく片長さ": [0.1, None, 0.3], "がく片幅": [0.2, 0.4, 0.9]})

        means = column_means(df, ["がく片長さ", "がく片幅"])

        assert means == pytest.approx({"がく片長さ": 0.2, "がく片幅": 0.5})
```

`pytest.approx` は辞書の値にも使えます。0.1 と 0.3 の平均は浮動小数点数の誤差で 0.2 ちょうどにならないことがあるので、近似値で比較します。

### 補完する

補完に使う値は引数で受け取ります。平均値を求める処理と補完する処理を分けておくと、「訓練データで求めた平均値でテストデータを補完する」という使い方ができます。元のデータを書き換えないことも、テストで約束しておきます。

```python
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
```

import に `column_means` と `fill_missing` を加えると、まだ定義していないので import の時点で失敗します。

```text
E   ImportError: cannot import name 'column_means' from 'lib.chapter02.iris_preprocessing'
```

```python
def column_means(df: pd.DataFrame, columns: list[str]) -> dict[str, float]:
    return {column: float(df[column].mean()) for column in columns}


def fill_missing(df: pd.DataFrame, values: dict[str, float]) -> pd.DataFrame:
    return df.fillna(values)
```

```text
============================== 6 passed in 0.37s ==============================
```

pandas の `mean()` は欠損値を除いて平均を求めます。`fillna` に辞書を渡すと、列ごとに違う値で補完した **新しい** データフレームを返します。`float(...)` で囲んでいるのは、NumPy の数値型ではなく Python の `float` にそろえて、戻り値の型 `dict[str, float]` と一致させるためです。

## 2.7 特徴量と正解ラベルに分ける

第 1 章と同じく、特徴量と正解ラベルを分けます。pandas では、正解ラベルの列を落としたデータフレームと、その列だけの `Series` に分けます。

```python
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
```

```python
def split_features_and_target(
    df: pd.DataFrame, target: str
) -> tuple[pd.DataFrame, pd.Series]:
    return df.drop(columns=[target]), df[target]
```

機械学習の分野では、特徴量を `x`、正解ラベル（target）を `t` や `y` と書く慣習があります。本シリーズでは書籍の表記に合わせて `x` と `t` を使います。

## 2.8 訓練データとテストデータに分ける

### 分割結果を表す型

分割の結果は 4 つのデータ（訓練用・テスト用の特徴量と正解ラベル）になります。タプルで返すと順番を取り違えやすいので、名前の付いたフィールドを持つデータクラスで返します。

```python
@dataclass(frozen=True)
class TrainTestSplit:
    x_train: pd.DataFrame
    x_test: pd.DataFrame
    t_train: pd.Series
    t_test: pd.Series
```

### 仮実装

最初のテストは件数だけを確かめます。0 から 9 までの番号を振った 10 件のデータを、テストデータの割合 0.3 で分けます。

```python
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
```

仮実装として、先頭 7 件と残りに分けます。

```python
def split_train_test(
    x: pd.DataFrame, t: pd.Series, test_size: float, seed: int
) -> TrainTestSplit:
    return TrainTestSplit(
        x_train=x.iloc[:7], x_test=x.iloc[7:], t_train=t.iloc[:7], t_test=t.iloc[7:]
    )
```

`iloc` は、行の **位置**（0 から数えた順番）で行を取り出します。

```text
============================== 8 passed in 0.47s ==============================
```

### 三角測量: 件数を一般化する

件数と割合が違う例を加えます。

```python
    def test_件数が変わってもテストデータの割合どおりに分ける(self) -> None:
        x, t = numbered_dataset(20)

        split = split_train_test(x, t, test_size=0.25, seed=0)

        assert (len(split.x_train), len(split.x_test)) == (15, 5)
        assert (len(split.t_train), len(split.t_test)) == (15, 5)
```

```text
E       assert (7, 13) == (15, 5)
E         
E         At index 0 diff: 7 != 15
```

テストデータの件数を「全体の件数 × 割合」から求めます。割り切れないときは、scikit-learn の `train_test_split` と同じく切り上げます。

```python
    n_train = len(x) - math.ceil(len(x) * test_size)
    return TrainTestSplit(
        x_train=x.iloc[:n_train],
        x_test=x.iloc[n_train:],
        t_train=t.iloc[:n_train],
        t_test=t.iloc[n_train:],
    )
```

### 三角測量: 並び順に頼らない分け方にする

件数の次は、分け方そのものの性質をテストします。

```python
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
```

先頭から順に分けるだけの今の実装でも、最初の 4 つは通ります。失敗するのは最後の 1 つだけです。

```text
test/chapter02/test_iris_preprocessing.py::TestSplitTrainTest::test_同じシードなら同じ分け方になる PASSED [ 92%]
test/chapter02/test_iris_preprocessing.py::TestSplitTrainTest::test_シードが違えば違う分け方になる FAILED [100%]
E       assert not True
======================== 1 failed, 12 passed in 0.48s =========================
```

先頭から順に分けると、データの並び順に偏りがあったとき（たとえば品種ごとに並んでいるとき）、テストデータが特定の品種だけになってしまいます。最後のテストは、この問題をシードの違いという形で捕まえています。

### Green: シード付きの乱数で並べ替える

NumPy の乱数生成器で行の位置をシャッフルしてから分けます。

```python
    positions = np.random.default_rng(seed).permutation(len(x))
    n_train = len(x) - math.ceil(len(x) * test_size)
    train, test = positions[:n_train], positions[n_train:]
    return TrainTestSplit(
        x_train=x.iloc[train],
        x_test=x.iloc[test],
        t_train=t.iloc[train],
        t_test=t.iloc[test],
    )
```

```text
============================= 13 passed in 0.36s ==============================
```

`np.random.default_rng(seed)` は、シード（乱数の種）が同じなら毎回同じ乱数列を返す生成器を作ります。`permutation(10)` は 0〜9 を並べ替えた配列を返します。機械学習では、分け方が実行のたびに変わると結果を比べられないので、シードを固定して **再現性** を確保します。

### scikit-learn と突き合わせる

scikit-learn にも同じ役割の `train_test_split` があります。件数の決め方が一致することを学習用テストで確かめます。

```python
    def test_scikit_learnのtrain_test_splitと同じ件数に分ける(self) -> None:
        x, t = numbered_dataset(150)

        split = split_train_test(x, t, test_size=0.3, seed=0)
        x_train, x_test = train_test_split(x, test_size=0.3, random_state=0)

        assert (len(split.x_train), len(split.x_test)) == (len(x_train), len(x_test))
```

件数は一致しますが、**どの行がテストデータに入るかは一致しません**。scikit-learn は内部で別の乱数の使い方をしているためです。シードの値が同じでも、実装が違えば分け方は変わります。本シリーズでは、ほかの言語の実装と結果を比べやすいように、自作の `split_train_test` を使い続けます。

## 2.9 前処理をまとめる

ここまでの関数を組み合わせて、「読み込む → 特徴量と正解ラベルに分ける → 訓練・テストデータに分ける → 訓練データの平均値で両方を補完する」をまとめた `prepare_iris` を作ります。

```python
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
```

```python
TARGET = "種類"


def prepare_iris(csv_file: Path, test_size: float, seed: int) -> TrainTestSplit:
    x, t = split_features_and_target(load_iris(csv_file), TARGET)
    split = split_train_test(x, t, test_size=test_size, seed=seed)
    means = column_means(split.x_train, list(x.columns))
    return replace(
        split,
        x_train=fill_missing(split.x_train, means),
        x_test=fill_missing(split.x_test, means),
    )
```

`dataclasses.replace` は、指定したフィールドだけを差し替えた **新しい** インスタンスを返します。`frozen=True` のデータクラスはフィールドを書き換えられないので、変更したいときは `replace` で作り直します。

## 2.10 実データで前処理の結果を表示する

### スキップの条件を共通化する

第 1 章では、テストファイルごとに `pytest.mark.skipif` の条件を書きました。これから章ごとに同じ条件を書くことになるので、ファイル名を渡すだけで使えるマーカーを `test/markers.py` にまとめます。

```python
# test/markers.py
import pytest

from lib.dataset import data_dir


def requires_data(*file_names: str) -> pytest.MarkDecorator:
    """学習データが配置されていなければテストをスキップするマーカーを返す。"""
    missing = [name for name in file_names if not (data_dir() / name).exists()]
    return pytest.mark.skipif(
        bool(missing),
        reason=f"学習データ {', '.join(missing)} が配置されていない（gulp data:setup）",
    )
```

### 実データのテスト

```python
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
```

```python
# lib/chapter02/__main__.py
import pandas as pd

from lib.chapter02.iris_preprocessing import (
    count_missing,
    load_iris,
    prepare_iris,
)
from lib.dataset import data_dir

TEST_SIZE = 0.3
SEED = 0


def format_counts(counts: pd.Series) -> str:
    return ", ".join(f"{column}={count}" for column, count in counts.items())


def main() -> None:
    csv_file = data_dir() / "iris.csv"
    df = load_iris(csv_file)
    split = prepare_iris(csv_file, test_size=TEST_SIZE, seed=SEED)
    print(f"データ件数: {len(df)}")
    print(f"欠損値の数: {format_counts(count_missing(df))}")
    print(f"訓練データ: {len(split.x_train)} 件, テストデータ: {len(split.x_test)} 件")
    missing_train = int(count_missing(split.x_train).sum())
    missing_test = int(count_missing(split.x_test).sum())
    print(
        f"補完後の欠損値の数: 訓練データ {missing_train}, テストデータ {missing_test}"
    )


if __name__ == "__main__":
    main()
```

```bash
uv run python -m lib.chapter02
```

```text
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
補完後の欠損値の数: 訓練データ 0, テストデータ 0
```

```bash
uv run pytest test/chapter02
```

```text
============================= 18 passed in 1.87s ==============================
```

データが無い環境では、実データのテスト 3 件がスキップされます。

```text
======================== 15 passed, 3 skipped in 1.58s ========================
```

## 2.11 Notebook で探索する

前処理ができたので、データの中身を Jupyter Lab で眺めます。Notebook はテストでは捉えにくい「データの傾向」をつかむための場所です。分かったことは、テストや次の章の実装に反映します。

Notebook は `apps/python/notebooks/chapter02_iris_exploration.ipynb` にあります。

```bash
uv run jupyter lab notebooks/chapter02_iris_exploration.ipynb
```

### 準備

```python
import sys

sys.path.append("..")

import seaborn as sns
from japanese_font import use_japanese_font

from lib.chapter02.iris_preprocessing import count_missing, load_iris, prepare_iris
from lib.dataset import data_dir

use_japanese_font();
```

Notebook は `notebooks/` で起動するので、`sys.path.append("..")` で `lib` を import できるようにします。これで、テスト済みの `prepare_iris` を Notebook からそのまま使えます。探索用のコードを Notebook に書き散らさず、テスト済みの関数を呼ぶだけにしておくと、Notebook で見た結果と本番コードの結果が食い違いません。

`use_japanese_font` は、グラフのラベルに日本語を表示するための補助関数です（`notebooks/japanese_font.py`）。

### 欠損値の分布

```python
count_missing(df).plot.bar(title="列ごとの欠損値の数");
```

棒グラフで見ると、欠損値は特徴量の 4 列に 1〜2 件ずつ散らばっていて、特定の列に偏っていないことが分かります。どの列も 150 件中 2 件以下なので、行ごと削除するより平均値で補完して件数を保つ方針を選びました。

### 品種ごとの特徴量

```python
split = prepare_iris(data_dir() / "iris.csv", test_size=0.3, seed=0)
train = split.x_train.assign(種類=split.t_train)
sns.pairplot(train, hue="種類");
```

`sns.pairplot` は、特徴量の 2 つずつの組み合わせを散布図にし、品種ごとに色分けします。探索には **訓練データだけ** を使います。テストデータを眺めてからモデルを決めると、テストデータの情報が設計に漏れるからです。

品種ごとの平均値も確認します。

```python
train.groupby("種類").mean().round(3)
```

```text
                 がく片長さ   がく片幅   花弁長さ    花弁幅
種類                                         
Iris-setosa      0.191  0.590  0.241  0.062
Iris-versicolor  0.437  0.309  0.520  0.507
Iris-virginica   0.627  0.413  0.699  0.754
```

散布図と平均値から、`Iris-setosa` は花弁長さ・花弁幅がほかの 2 品種よりはっきり小さいことが分かります。花弁の大きさで区切るだけで、少なくとも `Iris-setosa` は見分けられそうです。この「どの特徴量のどこで区切るか」をデータから自動で探すのが、第 3 章で作る決定木です。

グラフの画像は記事に載せていません。配布データの点をそのまま描いたグラフは、データの再配布に当たるおそれがあるためです。学習データを配置して、手元の Notebook で確認してください。

### 出力セルを消してからコミットする

Notebook の出力セルには、グラフや表としてデータが残ります。コミットの前に出力セルを消します。

```bash
uv run python tools/notebooks.py strip
```

出力セルが残っていないかは `uv run python tools/notebooks.py verify` で確認でき、CI でも検査しています。詳しくは第 6 章で扱います。

<details>
<summary>この章の完成コード（lib/chapter02/iris_preprocessing.py）</summary>

```python
import math
from dataclasses import dataclass, replace
from pathlib import Path

import numpy as np
import pandas as pd

TARGET = "種類"


@dataclass(frozen=True)
class TrainTestSplit:
    x_train: pd.DataFrame
    x_test: pd.DataFrame
    t_train: pd.Series
    t_test: pd.Series


def load_iris(csv_file: Path) -> pd.DataFrame:
    return pd.read_csv(csv_file)


def count_missing(df: pd.DataFrame) -> pd.Series:
    return df.isna().sum()


def column_means(df: pd.DataFrame, columns: list[str]) -> dict[str, float]:
    return {column: float(df[column].mean()) for column in columns}


def fill_missing(df: pd.DataFrame, values: dict[str, float]) -> pd.DataFrame:
    return df.fillna(values)


def split_features_and_target(
    df: pd.DataFrame, target: str
) -> tuple[pd.DataFrame, pd.Series]:
    return df.drop(columns=[target]), df[target]


def split_train_test(
    x: pd.DataFrame, t: pd.Series, test_size: float, seed: int
) -> TrainTestSplit:
    positions = np.random.default_rng(seed).permutation(len(x))
    n_train = len(x) - math.ceil(len(x) * test_size)
    train, test = positions[:n_train], positions[n_train:]
    return TrainTestSplit(
        x_train=x.iloc[train],
        x_test=x.iloc[test],
        t_train=t.iloc[train],
        t_test=t.iloc[test],
    )


def prepare_iris(csv_file: Path, test_size: float, seed: int) -> TrainTestSplit:
    x, t = split_features_and_target(load_iris(csv_file), TARGET)
    split = split_train_test(x, t, test_size=test_size, seed=seed)
    means = column_means(split.x_train, list(x.columns))
    return replace(
        split,
        x_train=fill_missing(split.x_train, means),
        x_test=fill_missing(split.x_test, means),
    )
```

</details>

## 2.12 まとめ

この章では、欠損値を含むデータの前処理と、訓練・テストデータへの分割を TDD で実装しました。

1. **学習用テスト** — pandas が BOM を取り除くこと、scikit-learn と件数の決め方が一致することを、テストで確かめた
2. **三角測量** — 件数の例を 2 つ用意して件数の計算を一般化し、さらに分け方の性質（重複なし・対応を保つ・シードによる再現性）のテストを重ねて、シャッフルが必要な理由をテストで示した
3. **データリークの防止** — 補完に使う平均値は訓練データだけから求めた
4. **再現性** — シードを固定して、実行のたびに同じ分け方になるようにした
5. **探索と本番コードの分離** — Notebook ではテスト済みの関数を呼び、訓練データだけを眺めた

Notebook での探索から、花弁の大きさで品種を見分けられそうだと分かりました。次の章では、この区切り方をデータから自動で探す決定木を自作し、scikit-learn の決定木と結果を突き合わせます。
