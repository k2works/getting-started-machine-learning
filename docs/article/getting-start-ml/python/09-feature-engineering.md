---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ボストンの住宅価格データを題材に、ダミー変数化・標準化・多項式特徴量・外れ値検出・表の結合を TDD で自作して scikit-learn と突き合わせ、特徴量ごとの決定係数の変化を測る。"
tags: [article,getting-start-ml,python]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T02:51:45Z }
---

# 第 9 章: 特徴量エンジニアリング

## 9.1 はじめに

第 7 章と第 8 章では、データの列をほぼそのままモデルに渡しました。しかし、モデルの性能はアルゴリズムよりも「どんな特徴量を渡すか」で大きく変わることがあります。元のデータから、モデルが学びやすい特徴量を作り出す作業を **特徴量エンジニアリング** と呼びます。

この章では、ボストンの住宅価格データを題材に、次の 5 つの技法を TDD で自作し、ライブラリ（pandas・scikit-learn）の結果と突き合わせます。

- カテゴリ値をダミー変数（0 と 1 の列）にする
- 特徴量を標準化する
- 2 乗の項と交互作用の項（多項式特徴量）を作る
- 外れ値を検出する
- 別の表を結合して特徴量を増やす

最後に、作った特徴量で線形回帰の決定係数がどう変わるかを実データで測ります。「特徴量を増やせば良くなる」「外れ値は除けば良くなる」とは限らないことが、数字で確かめられます。

## 9.2 題材とデータ

学習データの入手と配置は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。この章では 3 つのファイルを使います。

### Boston.csv

ボストン近郊の地域ごとの住宅価格のデータです。100 件、14 列あります。

| 列 | 意味 | 型 | 欠損 |
|----|------|----|------|
| CRIME | 犯罪率の水準 | 文字列（`high`・`low`・`very_low`） | なし |
| ZN・INDUS・CHAS・NOX・AGE・DIS・RAD・TAX・B | 地域の環境を表す指標 | 数値 | NOX と RAD に 1 件ずつ |
| RM | 住居あたりの平均部屋数 | 数値 | なし |
| PTRATIO | 生徒と教師の人数比 | 数値 | なし |
| LSTAT | 低所得者の割合（%） | 数値 | なし |
| PRICE | 住宅価格（目的変数） | 数値 | なし |

CRIME の件数は `very_low` が 50 件、`high` と `low` が 25 件ずつです。CRIME は数値ではないので、このままでは線形回帰に渡せません。

### bike.tsv と weather.csv

`bike.tsv` は、ある自転車シェアサービスの日ごとの利用者数（`cnt`）と天気 ID（`weather_id`）などを記録した 731 件のデータです。区切り文字がカンマではなく **タブ** です。

`weather.csv` は天気 ID と天気の名前（晴れ・曇り・雨）の対応表で、3 件です。文字コードが UTF-8 ではなく **Shift_JIS** です。

## 9.3 TODO リストの作成

**TODO リスト**:

- [ ] カテゴリ値をダミー変数にする
  - [ ] 先頭を除いたカテゴリを辞書順に求める
  - [ ] カテゴリごとに 0 と 1 の列を作る
  - [ ] pandas の `get_dummies` と同じ結果になる
- [ ] 特徴量を標準化する
  - [ ] 訓練データから平均と標準偏差を求める
  - [ ] 訓練データの平均と標準偏差で別のデータを標準化する
  - [ ] scikit-learn の `StandardScaler` と同じ値になる
- [ ] 多項式特徴量を作る
  - [ ] 2 乗の項を加える
  - [ ] 2 つの列の積（交互作用の項）を加える
  - [ ] scikit-learn の `PolynomialFeatures` と同じ列名と値になる
- [ ] IQR で外れ値を検出する
- [ ] タブ区切り・Shift_JIS のファイルを読み込んで結合する
- [ ] 特徴量の組み合わせごとに決定係数を測る

## 9.4 カテゴリ値をダミー変数にする

### カテゴリを求める

CRIME のような文字列の列は、カテゴリごとに「そのカテゴリなら 1、そうでなければ 0」の列に置き換えます。これを **ダミー変数** と呼びます。

カテゴリが 3 つなら、ダミー変数は 2 列で足ります。`low` でも `very_low` でもなければ `high` だと分かるからです。3 列すべて作ると、どれか 1 列が残りの列から計算できてしまい（多重共線性）、線形回帰の係数が不安定になります。そこで、辞書順で先頭のカテゴリを除きます。

```python
# test/chapter09/test_feature_engineering.py
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
```

```text
E   ModuleNotFoundError: No module named 'lib.chapter09.feature_engineering'
```

どちらもやることが明らかなので、明白な実装で進めます。

```python
# lib/chapter09/feature_engineering.py
def dummy_categories(values: pd.Series) -> list[str]:
    return sorted(values.dropna().unique())[1:]


def encode_dummies(
    df: pd.DataFrame, column: str, categories: list[str]
) -> pd.DataFrame:
    dummies = {
        f"{column}_{category}": (df[column] == category).astype(int)
        for category in categories
    }
    return df.drop(columns=[column]).assign(**dummies)
```

`df[column] == category` は、行ごとに一致するかどうかの真偽値の Series を返します。`astype(int)` で True を 1、False を 0 に変えています。

```text
============================== 2 passed in 0.39s ==============================
```

### カテゴリを引数で受け取る理由

`encode_dummies` がカテゴリの一覧を自分で求めずに引数で受け取るのは、訓練データとテストデータで **同じ列** を作るためです。テストデータに訓練データで見たことのない値が来ても、列は増えずにすべて 0 になるべきです。

```python
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
```

```text
============================== 4 passed in 0.37s ==============================
```

2 つ目のテストは、自作の実装が pandas の `get_dummies(drop_first=True)` と同じ列名・同じ値になることを確かめる学習用テストです。`pd.testing.assert_frame_equal` は、列名・値・型まで含めて 2 つの DataFrame が一致するかを調べます。`get_dummies` は手軽ですが、渡したデータに含まれるカテゴリだけで列を作るので、訓練データとテストデータで別々に呼ぶと列がずれることがあります。

## 9.5 特徴量を標準化する

### 標準化とは

Boston データの列は、単位も大きさもばらばらです。RM（部屋数）は 6 前後、TAX（税率）は数百です。**標準化** は、各列から平均を引いて標準偏差で割り、どの列も平均 0・標準偏差 1 にそろえる変換です。

$$z = \frac{x - \text{平均}}{\text{標準偏差}}$$

標準化で大事なのは、平均と標準偏差を **訓練データだけから求め**、その値でテストデータも変換することです。テストデータの平均を使うと、本来は未知であるはずのテストデータの情報がモデルの準備に漏れてしまいます。そこで、求める処理（`fit`）と変換する処理（`transform`）を分けた `Standardizer` を作ります。

### Red: 標準偏差の求め方

```python
class TestStandardizer:
    def test_訓練データから列ごとの平均と標準偏差を求める(self) -> None:
        df = pd.DataFrame({"RM": [1.0, 2.0, 3.0], "LSTAT": [10.0, 10.0, 40.0]})

        standardizer = Standardizer.fit(df)

        assert standardizer.means == pytest.approx({"RM": 2.0, "LSTAT": 20.0})
        assert standardizer.stds == pytest.approx(
            {"RM": (2 / 3) ** 0.5, "LSTAT": 200**0.5}
        )
```

RM の平均は 2 で、平均からの差の 2 乗は 1・0・1 です。その平均 2/3 の平方根が標準偏差です。pandas の `std()` を素直に使って実装してみます。

```python
@dataclass(frozen=True)
class Standardizer:
    means: dict[str, float]
    stds: dict[str, float]

    @classmethod
    def fit(cls, df: pd.DataFrame) -> "Standardizer":
        return cls(
            means={column: float(df[column].mean()) for column in df.columns},
            stds={column: float(df[column].std()) for column in df.columns},
        )
```

```text
E       AssertionError: assert {'RM': 1.0, '...0508075688775} == approx({'RM':...51 ± 1.4e-05})
E         
E         comparison failed. Mismatched elements: 2 / 2:
E         Max absolute difference: 3.1783724519578236
E         Max relative difference: 0.22474487139158913
E         Index | Obtained           | Expected                    
E         RM    | 1.0                | 0.816496580927726 ± 8.2e-07 
E         LSTAT | 17.320508075688775 | 14.142135623730951 ± 1.4e-05
```

RM の標準偏差が 0.816 ではなく 1.0 になりました。pandas の `std()` は、既定で差の 2 乗の合計を「件数 − 1」で割る **不偏標準偏差**（`ddof=1`）を返すからです。scikit-learn の `StandardScaler` は「件数」で割る **標準偏差**（`ddof=0`）を使います。ライブラリと結果を一致させるため、`ddof=0` を指定します。

```python
            stds={column: float(df[column].std(ddof=0)) for column in df.columns},
```

```text
============================== 5 passed in 0.38s ==============================
```

### 別のデータを標準化する

`transform` は、`fit` で求めた平均と標準偏差をそのまま使って変換します。テストでは、平均 2・標準偏差 0.5 の `Standardizer` を直接作って確かめます。

```python
    def test_訓練データの平均と標準偏差で別のデータを標準化する(self) -> None:
        standardizer = Standardizer(means={"RM": 2.0}, stds={"RM": 0.5})
        other = pd.DataFrame({"RM": [1.0, 2.0, 4.0]})

        standardized = standardizer.transform(other)

        assert standardized["RM"].to_list() == pytest.approx([-2.0, 0.0, 4.0])
```

```text
E       AttributeError: 'Standardizer' object has no attribute 'transform'
```

```python
    def transform(self, df: pd.DataFrame) -> pd.DataFrame:
        return df.assign(
            **{
                column: (df[column] - self.means[column]) / self.stds[column]
                for column in self.means
            }
        )
```

```text
============================== 6 passed in 0.38s ==============================
```

### すべて同じ値の列

CHAS（川沿いかどうか）のように 0 と 1 しかない列は、訓練データの取り方によってはすべて同じ値になります。すると標準偏差が 0 になり、0 での割り算が起きます。

```python
    def test_すべて同じ値の列は0にする(self) -> None:
        df = pd.DataFrame({"CHAS": [1.0, 1.0, 1.0]})

        standardized = Standardizer.fit(df).transform(df)

        assert standardized["CHAS"].to_list() == [0.0, 0.0, 0.0]
```

```text
E       assert [nan, nan, nan] == [0.0, 0.0, 0.0]
```

pandas では 0 を 0 で割ると例外にならず、NaN（欠損値）になります。黙って NaN が混ざると、後の学習で原因の分かりにくいエラーになります。scikit-learn の `StandardScaler` と同じく、標準偏差が 0 のときは 1 で割るようにします。`or 1.0` は、左側が 0.0（偽とみなされる値）のときだけ右側の 1.0 を使う書き方です。

```python
            stds={
                column: float(df[column].std(ddof=0)) or 1.0 for column in df.columns
            },
```

```text
============================== 7 passed in 0.38s ==============================
```

最後に、scikit-learn の `StandardScaler` と同じ値になることを学習用テストで確かめます。訓練データで `fit` し、別のテストデータを `transform` する流れまで同じにしています。

```python
    def test_scikit_learnのStandardScalerと同じ値になる(self) -> None:
        train = pd.DataFrame(
            {"RM": [5.5, 6.0, 7.5, 6.5], "LSTAT": [12.0, 4.0, 9.0, 3.0]}
        )
        test = pd.DataFrame({"RM": [6.2, 8.0], "LSTAT": [15.0, 2.0]})

        standardized = Standardizer.fit(train).transform(test)
        expected = StandardScaler().fit(train).transform(test)

        assert standardized.to_numpy() == pytest.approx(expected)
```

```text
============================== 8 passed in 1.22s ==============================
```

## 9.6 多項式特徴量を作る

### 2 乗の項

線形回帰は「特徴量 × 係数」の足し算でしか予測できません。価格が部屋数の 2 乗に比例して増えるような曲線の関係は、部屋数の列だけでは表せません。そこで、部屋数の 2 乗の列を特徴量として加えます。モデルは線形のままでも、曲線の関係を表せるようになります。

```python
class TestPolynomialFeatures:
    def test_1列なら元の列と2乗の列を返す(self) -> None:
        df = pd.DataFrame({"RM": [2.0, 3.0]})

        features = polynomial_features(df, ["RM"])

        assert features.to_dict(orient="list") == {"RM": [2.0, 3.0], "RM^2": [4.0, 9.0]}
```

2 乗の列を加えるだけの実装から始めます。

```python
def polynomial_features(df: pd.DataFrame, columns: list[str]) -> pd.DataFrame:
    squares = {f"{column}^2": df[column] ** 2 for column in columns}
    return df[columns].assign(**squares)
```

```text
============================== 9 passed in 1.49s ==============================
```

### 三角測量: 交互作用の項

2 列を渡したときは、2 乗の項に加えて、2 つの列の積（**交互作用の項**）も作ります。「部屋数が多く、かつ低所得者の割合が低い」ような組み合わせの効果を表すためです。列の並び順も scikit-learn に合わせたいので、列名の順序も確かめます。

```python
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
```

```text
E       AssertionError: assert ['RM', 'LSTAT...2', 'LSTAT^2'] == ['RM', 'LSTAT...T', 'LSTAT^2']
E         
E         At index 3 diff: 'LSTAT^2' != 'RM LSTAT'
E         Right contains one more item: 'LSTAT^2'
```

2 乗の項と交互作用の項は、「列の組を、同じ列を 2 回選ぶことも許して選ぶ」ことで一度に作れます。標準ライブラリの `itertools.combinations_with_replacement` がちょうどその組を返します。`["RM", "LSTAT"]` なら `(RM, RM)`・`(RM, LSTAT)`・`(LSTAT, LSTAT)` の順です。

```python
def polynomial_features(df: pd.DataFrame, columns: list[str]) -> pd.DataFrame:
    products = {
        term_name(left, right): df[left] * df[right]
        for left, right in combinations_with_replacement(columns, 2)
    }
    return df[columns].assign(**products)


def term_name(left: str, right: str) -> str:
    return f"{left}^2" if left == right else f"{left} {right}"
```

```text
============================= 10 passed in 3.10s ==============================
```

列名を `RM^2`・`RM LSTAT` という形にしたのは、scikit-learn の `PolynomialFeatures` の `get_feature_names_out()` が返す名前と同じにするためです。3 列で突き合わせます。

```python
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
```

```text
============================= 11 passed in 1.62s ==============================
```

`include_bias=False` は、すべて 1 の定数列を作らない指定です。定数項は線形回帰の切片が担うので不要です。3 列から作られる列は、元の 3 列・2 乗の 3 列・交互作用の 3 列の合計 9 列です。列の数は元の列数の 2 乗に近い速さで増えるので、むやみに作ると学習データの件数に対して特徴量が多くなりすぎます。この影響は 9.9 節で実測します。

## 9.7 外れ値を検出する

他の値から大きく離れた値を **外れ値** と呼びます。ここでは、箱ひげ図でも使われる **IQR（四分位範囲）** による方法を実装します。値を小さい順に並べて 25% の位置の値を第 1 四分位数（Q1）、75% の位置の値を第 3 四分位数（Q3）とし、その差 IQR = Q3 − Q1 を求めます。Q1 − 1.5 × IQR より小さい値と、Q3 + 1.5 × IQR より大きい値を外れ値とみなします。

まず上側の外れ値のテストを書きます。`[1, 2, 3, 4, 100]` なら Q1 = 2、Q3 = 4、IQR = 2 なので、4 + 1.5 × 2 = 7 を超える 100 が外れ値です。

```python
class TestIqrOutliers:
    def test_第3四分位数からIQRの15倍より大きい値を外れ値とする(self) -> None:
        values = pd.Series([1.0, 2.0, 3.0, 4.0, 100.0])

        assert iqr_outliers(values).to_list() == [False, False, False, False, True]
```

```python
def iqr_outliers(values: pd.Series, k: float = 1.5) -> pd.Series:
    q1, q3 = values.quantile(0.25), values.quantile(0.75)
    return values > q3 + k * (q3 - q1)
```

```text
============================= 12 passed in 1.26s ==============================
```

下側の外れ値のテストで三角測量します。

```python
    def test_第1四分位数からIQRの15倍より小さい値も外れ値とする(self) -> None:
        values = pd.Series([-100.0, 1.0, 2.0, 3.0, 4.0])

        assert iqr_outliers(values).to_list() == [True, False, False, False, False]
```

```text
E       assert [False, False... False, False] == [True, False,... False, False]
E         
E         At index 0 diff: False != True
```

```python
def iqr_outliers(values: pd.Series, k: float = 1.5) -> pd.Series:
    q1, q3 = values.quantile(0.25), values.quantile(0.75)
    iqr = q3 - q1
    return (values < q1 - k * iqr) | (values > q3 + k * iqr)
```

```text
============================= 13 passed in 1.22s ==============================
```

pandas の真偽値の Series 同士は、`and`・`or` ではなく `&`・`|` で要素ごとに組み合わせます。演算子の優先順位のため、比較式はかっこで囲みます。

## 9.8 表を結合して特徴量を増やす

### タブ区切りと Shift_JIS

自転車の利用者数の表には天気 ID しかなく、それが晴れなのか雨なのかは別の表にあります。2 つの表を天気 ID で **結合** すれば、天気の名前を特徴量として使えます。まず読み込みです。

```python
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
```

タブ区切りは `sep="\t"` で指定します。天気の表は、まず文字コードを指定せずに読み込んでみます。

```python
def load_bike(tsv_file: Path) -> pd.DataFrame:
    return pd.read_csv(tsv_file, sep="\t")


def load_weather(csv_file: Path) -> pd.DataFrame:
    return pd.read_csv(csv_file)
```

```text
E   UnicodeDecodeError: 'utf-8' codec can't decode byte 0x90 in position 22: invalid start byte
```

pandas は既定で UTF-8 として読むので、Shift_JIS で書かれた「晴」の先頭バイトを解釈できずに失敗しました。テストのフィクスチャを実データと同じ Shift_JIS で書き出していたので、この問題をテストで捕まえられました。文字コードを指定します。

```python
def load_weather(csv_file: Path) -> pd.DataFrame:
    return pd.read_csv(csv_file, encoding="shift_jis")
```

```text
============================= 15 passed in 1.27s ==============================
```

### 結合と集計

```python
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
```

```python
def join_weather(bike: pd.DataFrame, weather: pd.DataFrame) -> pd.DataFrame:
    return bike.merge(weather, how="inner", on="weather_id")


def mean_count_by_weather(joined: pd.DataFrame) -> pd.Series:
    return joined.groupby("weather")["cnt"].mean().sort_values(ascending=False)
```

```text
============================= 18 passed in 1.28s ==============================
```

`merge(how="inner")` は **内部結合** で、両方の表にある天気 ID の行だけを残します。2 つ目のテストのように、天気の表に無い ID の行は消えます。行を消したくない場合は `how="left"` を使い、天気の名前を欠損値にします。どちらを選ぶかは、結合の前後で件数が変わってよいかどうかで決めます。実データでは、結合の前後とも 731 件でした。

## 9.9 特徴量の効果を測る

### Boston データの前処理

ここまでの部品と、第 2 章の分割・欠損値補完の関数を組み合わせて、Boston データを前処理します。

```python
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
```

```python
def prepare_boston(csv_file: Path, test_size: float, seed: int) -> TrainTestSplit:
    df = pd.read_csv(csv_file)
    encoded = encode_dummies(df, "CRIME", dummy_categories(df["CRIME"]))
    x, t = split_features_and_target(encoded, TARGET)
    split = split_train_test(x, t, test_size=test_size, seed=seed)
    means = column_means(split.x_train, list(x.columns))
    return replace(
        split,
        x_train=fill_missing(split.x_train, means),
        x_test=fill_missing(split.x_test, means),
    )
```

```text
============================= 19 passed in 1.36s ==============================
```

欠損値を埋める平均は訓練データだけから求めますが、ダミー変数のカテゴリの一覧はデータ全体から求めています。カテゴリの一覧は「CRIME がどんな値を取りうるか」というデータの定義で、価格の情報を含まないからです。訓練データだけから求めると、4 件のフィクスチャのように訓練データに現れなかったカテゴリの列が作られず、テストデータと列がそろわなくなります。

### 特徴量の組み合わせごとに決定係数を測る

多項式特徴量の中から使う列（`terms`）を選び、標準化してから線形回帰で学習し、訓練データとテストデータの決定係数を返す関数を作ります。線形回帰は第 7 章で自作したので、ここでは scikit-learn の `LinearRegression` を使います。

テストでは、価格が部屋数の 2 次式（3 × RM² + 1）になっている架空のデータを使います。

```python
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
```

```python
def score_feature_set(
    split: TrainTestSplit, columns: list[str], terms: list[str]
) -> tuple[float, float]:
    x_train = polynomial_features(split.x_train, columns)[terms]
    x_test = polynomial_features(split.x_test, columns)[terms]
    standardizer = Standardizer.fit(x_train)
    x_train, x_test = standardizer.transform(x_train), standardizer.transform(x_test)
    model = LinearRegression().fit(x_train, split.t_train)
    return (
        float(model.score(x_train, split.t_train)),
        float(model.score(x_test, split.t_test)),
    )
```

```text
============================= 21 passed in 1.92s ==============================
```

RM だけでは直線しか引けないので決定係数は 1 に届きません。RM² を加えると、訓練データにも、学習に使っていない RM = 5・6 のテストデータにも完全に当てはまります。

### 外れ値を除いて学習する

外れ値の影響も測れるように、訓練データから価格が外れ値の行を除く関数を作ります。テストデータは実際に予測する対象なので、除きません。

```python
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
```

```python
def remove_target_outliers(split: TrainTestSplit) -> TrainTestSplit:
    keep = ~iqr_outliers(split.t_train)
    return replace(split, x_train=split.x_train[keep], t_train=split.t_train[keep])
```

```text
============================= 22 passed in 1.51s ==============================
```

`~` は真偽値の Series の各要素を反転します。「外れ値でない行」を残すために使っています。

### 実データで測る

特徴量には RM・LSTAT・PTRATIO を使います。訓練データで各列と価格の相関係数を求めると、絶対値の大きい順に RM（0.711）・LSTAT（0.640）・PTRATIO（0.500）となり、4 番目の INDUS（0.350）以下とは差があるからです。この 3 列から、特徴量の組を 3 通り作って比べます。

```python
# lib/chapter09/__main__.py
TEST_SIZE = 0.3
SEED = 0
COLUMNS = ["RM", "LSTAT", "PTRATIO"]
SQUARES = ["RM^2", "LSTAT^2", "PTRATIO^2"]
FEATURE_SETS = {
    "元の特徴量": COLUMNS,
    "2 乗の項を追加": COLUMNS + SQUARES,
    "交互作用の項も追加": list(
        polynomial_features(pd.DataFrame(columns=COLUMNS), COLUMNS).columns
    ),
}
```

「交互作用の項も追加」の列名は、空の DataFrame を `polynomial_features` に渡して列名だけを取り出しています。列名の組み立て方を 1 か所（`term_name`）にとどめるためです。

```bash
uv run python -m lib.chapter09
```

```text
訓練データ: 70 件, テストデータ: 30 件
特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
決定係数:
  元の特徴量（3 列）: 訓練 0.6730, テスト 0.5848
  2 乗の項を追加（6 列）: 訓練 0.8375, テスト 0.7283
  交互作用の項も追加（9 列）: 訓練 0.8643, テスト 0.5799
訓練データの PRICE の外れ値: 7 件
  外れ値を除いて 2 乗の項を追加: 訓練 0.6443, テスト 0.6268
天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
```

結果を表にまとめます。

| 特徴量 | 列数 | 訓練データ | テストデータ |
|--------|------|-----------|------------|
| 元の特徴量 | 3 | 0.6730 | 0.5848 |
| 2 乗の項を追加 | 6 | 0.8375 | **0.7283** |
| 交互作用の項も追加 | 9 | 0.8643 | 0.5799 |
| 外れ値を除いて 2 乗の項を追加 | 6 | 0.6443 | 0.6268 |

この表から、次のことが読み取れます。

- **2 乗の項は効いた**: テストデータの決定係数が 0.5848 から 0.7283 に上がりました。価格と部屋数・低所得者の割合の関係が直線ではなく曲線であることを、2 乗の項が捉えています（9.10 節の散布図でも確認します）
- **交互作用の項は逆効果だった**: 訓練データの決定係数は 0.8643 とさらに上がったのに、テストデータでは 0.5799 と、元の特徴量より下がりました。訓練データ 70 件に対して 9 列は多く、訓練データの偶然のばらつきまで覚えてしまった **過学習** の状態です
- **外れ値を除いても良くならなかった**: IQR で検出した 7 件を除くと、テストデータの決定係数は 0.7283 から 0.6268 に下がりました。価格の高い地域は「測定の誤り」ではなく「実際に高い」データなので、除くとモデルは高価格帯を学べなくなり、テストデータに含まれる高価格帯の予測を外すようになります

外れ値の **検出** は機械的にできますが、除くかどうかはデータの意味を見て決める必要があります。そして、特徴量を増やすかどうかは、必ず学習に使っていないテストデータの評価で判断します。

実データのテストでは、この結果を固定しています。

```python
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
```

`pytest.approx(..., abs=1e-4)` は、小数第 4 位までの誤差を許して比較します。`@requires_data` は `test/markers.py` にあるマーカーで、指定したファイルが 1 つでも無ければクラス内のテストをスキップします。

```bash
uv run pytest test/chapter09 --cov=lib/chapter09 --cov-report=term-missing
```

```text
Name                                   Stmts   Miss  Cover   Missing
--------------------------------------------------------------------
lib\chapter09\__init__.py                  0      0   100%
lib\chapter09\__main__.py                 30      0   100%
lib\chapter09\feature_engineering.py      55      0   100%
--------------------------------------------------------------------
TOTAL                                     85      0   100%
============================= 26 passed in 2.87s ==============================
```

学習データが無い環境では、実データのテスト 4 件がスキップされ、22 件が通ります。

## 9.10 Notebook による探索と可視化

テストで確かめた結果を、グラフでも確認します。Notebook は `apps/python/notebooks/chapter09_boston_exploration.ipynb` にあります。学習データを再配布しないため、この記事にはグラフの画像を載せていません。手元で Notebook を実行して確認してください。

```bash
uv run jupyter lab notebooks/chapter09_boston_exploration.ipynb
```

### 準備

```python
import sys

sys.path.append("..")

import matplotlib.pyplot as plt
import seaborn as sns
from japanese_font import use_japanese_font

from lib.chapter09.__main__ import COLUMNS
from lib.chapter09.feature_engineering import (
    Standardizer,
    iqr_outliers,
    join_weather,
    load_bike,
    load_weather,
    mean_count_by_weather,
    prepare_boston,
)
from lib.dataset import data_dir

use_japanese_font();
```

Notebook からもテスト済みの `lib` の関数を使います。Notebook の中で前処理を書き直すと、テストしたコードと Notebook のコードが食い違っていくからです。

### 標準化の前後で分布を比べる

```python
split = prepare_boston(data_dir() / "Boston.csv", test_size=0.3, seed=0)
train = split.x_train[COLUMNS]
standardized = Standardizer.fit(train).transform(train)
train.shape
```

```text
(70, 3)
```

```python
fig, axes = plt.subplots(2, 3, figsize=(12, 6))
for i, column in enumerate(COLUMNS):
    train[column].plot.hist(ax=axes[0, i], title=f"{column}（標準化前）")
    standardized[column].plot.hist(ax=axes[1, i], title=f"{column}（標準化後）")
fig.tight_layout();
```

上段が標準化前、下段が標準化後のヒストグラムです。RM と LSTAT は棒の並びがそのままで、横軸の目盛りだけが 0 を中心とした値に変わっていることが分かります。PTRATIO は棒の高さが少し違って見えますが、これはヒストグラムの区間の区切り方が横軸の範囲に合わせて変わるためで、分布の偏り方は同じです。標準化は分布の形を変えずに、位置と幅をそろえる変換です。

```python
{
    "標準化前の平均": train.mean().round(2).to_dict(),
    "標準化後の標準偏差": standardized.std(ddof=0).round(2).to_dict(),
}
```

```text
{'標準化前の平均': {'RM': 6.25, 'LSTAT': 11.73, 'PTRATIO': 18.51},
 '標準化後の標準偏差': {'RM': 1.0, 'LSTAT': 1.0, 'PTRATIO': 1.0}}
```

### 特徴量と価格の関係を見る

```python
priced = train.assign(PRICE=split.t_train)
fig, axes = plt.subplots(1, 3, figsize=(12, 4))
for ax, column in zip(axes, COLUMNS, strict=True):
    sns.scatterplot(data=priced, x=column, y="PRICE", ax=ax)
fig.tight_layout();
```

```python
priced.corr()["PRICE"].round(3)
```

```text
RM         0.711
LSTAT     -0.640
PTRATIO   -0.500
PRICE      1.000
Name: PRICE, dtype: float64
```

散布図からは次のことが読み取れます。

- **RM**: 部屋数が多いほど価格が高く、部屋数が特に多い範囲では価格が急に上がります
- **LSTAT**: 低所得者の割合が高いほど価格が低く、割合が低い範囲で価格が急に上がる、下に凸の曲線になっています
- **PTRATIO**: 右下がりの傾向はありますが、特定の値に点が縦に並んでいて、ばらつきが大きいです

RM と LSTAT の関係が直線ではなく曲線であることが、2 乗の項で決定係数が上がった理由です。相関係数は直線的な関係の強さしか表さないので、散布図で形を見ることが大切です。

### 外れ値を見る

```python
split.t_train.plot.box(title="訓練データの PRICE")
int(iqr_outliers(split.t_train).sum())
```

```text
7
```

箱ひげ図で丸く描かれる点が、IQR で外れ値とみなされた 7 件です。そのうち 6 件は価格の高い側にあり、1 件だけが低い側にあります。散布図と見比べると、価格の高い点は部屋数が多く低所得者の割合が低い地域で、特徴量と矛盾しない値です。高価格帯の地域が少ないだけで、誤ったデータとは言えません。これが、外れ値を除くとテストデータの決定係数が下がった理由です。

### 天気ごとの利用者数を見る

```python
joined = join_weather(
    load_bike(data_dir() / "bike.tsv"), load_weather(data_dir() / "weather.csv")
)
mean_count_by_weather(joined).plot.bar(title="天気ごとの平均利用者数");
```

晴れ（4876.8 人）・曇り（4052.7 人）・雨（1803.3 人）の順に利用者が少なくなり、雨の日は晴れの日の半分以下です。天気 ID という数字のままでは分からなかったこの傾向が、表を結合したことで見えるようになりました。天気を利用者数の予測に使うなら、天気の名前をダミー変数にして特徴量に加えます。

### Notebook の片付け

Notebook の出力には学習データ由来のグラフが含まれるので、コミットする前に出力セルを消します。

```bash
uv run tox -e format
```

## 9.11 リファクタリング

TDD でテストを 1 つずつ足していく間、関数はモジュールの末尾に追記していきました。その結果、`feature_engineering.py` の関数の並びが、読む人にとって自然な順序になっていませんでした。テストが揃ったので、振る舞いを変えずに「ダミー変数 → 標準化 → 多項式特徴量 → 外れ値 → Boston データの前処理と評価 → 表の結合」の順に並べ替えました。並べ替えの後も 26 件のテストが通ることを確認しています。

<details>
<summary>この章の完成コード（lib/chapter09/feature_engineering.py）</summary>

```python
from dataclasses import dataclass, replace
from itertools import combinations_with_replacement
from pathlib import Path

import pandas as pd
from sklearn.linear_model import LinearRegression

from lib.chapter02.iris_preprocessing import (
    TrainTestSplit,
    column_means,
    fill_missing,
    split_features_and_target,
    split_train_test,
)

TARGET = "PRICE"


def dummy_categories(values: pd.Series) -> list[str]:
    return sorted(values.dropna().unique())[1:]


def encode_dummies(
    df: pd.DataFrame, column: str, categories: list[str]
) -> pd.DataFrame:
    dummies = {
        f"{column}_{category}": (df[column] == category).astype(int)
        for category in categories
    }
    return df.drop(columns=[column]).assign(**dummies)


@dataclass(frozen=True)
class Standardizer:
    means: dict[str, float]
    stds: dict[str, float]

    @classmethod
    def fit(cls, df: pd.DataFrame) -> "Standardizer":
        return cls(
            means={column: float(df[column].mean()) for column in df.columns},
            stds={
                column: float(df[column].std(ddof=0)) or 1.0 for column in df.columns
            },
        )

    def transform(self, df: pd.DataFrame) -> pd.DataFrame:
        return df.assign(
            **{
                column: (df[column] - self.means[column]) / self.stds[column]
                for column in self.means
            }
        )


def polynomial_features(df: pd.DataFrame, columns: list[str]) -> pd.DataFrame:
    products = {
        term_name(left, right): df[left] * df[right]
        for left, right in combinations_with_replacement(columns, 2)
    }
    return df[columns].assign(**products)


def term_name(left: str, right: str) -> str:
    return f"{left}^2" if left == right else f"{left} {right}"


def iqr_outliers(values: pd.Series, k: float = 1.5) -> pd.Series:
    q1, q3 = values.quantile(0.25), values.quantile(0.75)
    iqr = q3 - q1
    return (values < q1 - k * iqr) | (values > q3 + k * iqr)


def prepare_boston(csv_file: Path, test_size: float, seed: int) -> TrainTestSplit:
    df = pd.read_csv(csv_file)
    encoded = encode_dummies(df, "CRIME", dummy_categories(df["CRIME"]))
    x, t = split_features_and_target(encoded, TARGET)
    split = split_train_test(x, t, test_size=test_size, seed=seed)
    means = column_means(split.x_train, list(x.columns))
    return replace(
        split,
        x_train=fill_missing(split.x_train, means),
        x_test=fill_missing(split.x_test, means),
    )


def remove_target_outliers(split: TrainTestSplit) -> TrainTestSplit:
    keep = ~iqr_outliers(split.t_train)
    return replace(split, x_train=split.x_train[keep], t_train=split.t_train[keep])


def score_feature_set(
    split: TrainTestSplit, columns: list[str], terms: list[str]
) -> tuple[float, float]:
    x_train = polynomial_features(split.x_train, columns)[terms]
    x_test = polynomial_features(split.x_test, columns)[terms]
    standardizer = Standardizer.fit(x_train)
    x_train, x_test = standardizer.transform(x_train), standardizer.transform(x_test)
    model = LinearRegression().fit(x_train, split.t_train)
    return (
        float(model.score(x_train, split.t_train)),
        float(model.score(x_test, split.t_test)),
    )


def load_bike(tsv_file: Path) -> pd.DataFrame:
    return pd.read_csv(tsv_file, sep="\t")


def load_weather(csv_file: Path) -> pd.DataFrame:
    return pd.read_csv(csv_file, encoding="shift_jis")


def join_weather(bike: pd.DataFrame, weather: pd.DataFrame) -> pd.DataFrame:
    return bike.merge(weather, how="inner", on="weather_id")


def mean_count_by_weather(joined: pd.DataFrame) -> pd.Series:
    return joined.groupby("weather")["cnt"].mean().sort_values(ascending=False)
```

</details>

<details>
<summary>この章の完成コード（lib/chapter09/__main__.py）</summary>

```python
import pandas as pd

from lib.chapter09.feature_engineering import (
    Standardizer,
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

TEST_SIZE = 0.3
SEED = 0
COLUMNS = ["RM", "LSTAT", "PTRATIO"]
SQUARES = ["RM^2", "LSTAT^2", "PTRATIO^2"]
FEATURE_SETS = {
    "元の特徴量": COLUMNS,
    "2 乗の項を追加": COLUMNS + SQUARES,
    "交互作用の項も追加": list(
        polynomial_features(pd.DataFrame(columns=COLUMNS), COLUMNS).columns
    ),
}


def format_scores(scores: tuple[float, float]) -> str:
    train_score, test_score = scores
    return f"訓練 {train_score:.4f}, テスト {test_score:.4f}"


def main() -> None:
    split = prepare_boston(data_dir() / "Boston.csv", test_size=TEST_SIZE, seed=SEED)
    print(f"訓練データ: {len(split.x_train)} 件, テストデータ: {len(split.x_test)} 件")
    print(f"特徴量の列: {', '.join(split.x_train.columns)}")

    standardized = Standardizer.fit(split.x_train).transform(split.x_train)
    # 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 に丸める
    mean = round(float(standardized["RM"].mean()), 6) or 0.0
    std = float(standardized["RM"].std(ddof=0))
    print(f"標準化した訓練データの RM: 平均 {mean:.2f}, 標準偏差 {std:.2f}")

    print("決定係数:")
    for name, terms in FEATURE_SETS.items():
        scores = score_feature_set(split, COLUMNS, terms)
        print(f"  {name}（{len(terms)} 列）: {format_scores(scores)}")

    outliers = int(iqr_outliers(split.t_train).sum())
    print(f"訓練データの PRICE の外れ値: {outliers} 件")
    scores = score_feature_set(
        remove_target_outliers(split), COLUMNS, COLUMNS + SQUARES
    )
    print(f"  外れ値を除いて 2 乗の項を追加: {format_scores(scores)}")

    joined = join_weather(
        load_bike(data_dir() / "bike.tsv"), load_weather(data_dir() / "weather.csv")
    )
    means = mean_count_by_weather(joined)
    print(
        "天気ごとの平均利用者数: "
        + ", ".join(f"{weather}={count:.1f}" for weather, count in means.items())
    )


if __name__ == "__main__":
    main()
```

</details>

## 9.12 まとめ

この章では、特徴量エンジニアリングの 5 つの技法を TDD で自作し、ライブラリの結果と突き合わせました。

| 技法 | 自作した関数・クラス | 突き合わせたライブラリ | 落とし穴 |
|------|------------------|-------------------|---------|
| ダミー変数 | `dummy_categories`・`encode_dummies` | `pd.get_dummies` | 訓練データとテストデータで列をそろえる |
| 標準化 | `Standardizer` | `StandardScaler` | 標準偏差の `ddof`、分散 0 の列、テストデータの平均を使わない |
| 多項式特徴量 | `polynomial_features` | `PolynomialFeatures` | 列数が急に増えて過学習しやすい |
| 外れ値の検出 | `iqr_outliers` | — | 検出はできても、除くかどうかはデータの意味で決める |
| 表の結合 | `load_bike`・`load_weather`・`join_weather` | `DataFrame.merge` | 区切り文字と文字コード、内部結合で消える行 |

実データでは、2 乗の項を加えるとテストデータの決定係数が 0.5848 から 0.7283 に上がりました。一方で、交互作用の項まで加えると 0.5799 に下がり、外れ値を除くと 0.6268 に下がりました。特徴量を作るのは手段で、その効果は学習に使っていないデータで測って判断します。

次の章では、分類のモデルを増やし、ロジスティック回帰と、第 3 章の決定木を組み合わせたランダムフォレストを実装します。
