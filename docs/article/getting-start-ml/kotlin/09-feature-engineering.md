---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・外れ値検出・Shift_JIS の表の結合を TDD で自作し、特徴量の組み合わせごとの決定係数を測って、Tribuo の標準化との違い（不偏標準偏差）を確かめる。"
tags: [article,getting-start-ml,kotlin]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T06:01:20Z }
---

# 第 9 章: 特徴量エンジニアリング

## 9.1 はじめに

第 7 章と第 8 章では、データの列をほぼそのままモデルに渡しました。しかし、モデルの性能はアルゴリズムよりも「どんな特徴量を渡すか」で大きく変わることがあります。元のデータから、モデルが学びやすい特徴量を作り出す作業を **特徴量エンジニアリング** と呼びます。

この章では、ボストンの住宅価格データを題材に、次の 5 つの技法を TDD で自作します。

- カテゴリ値をダミー変数（0 と 1 の列）にする
- 特徴量を標準化する
- 2 乗の項と交互作用の項（多項式特徴量）を作る
- 外れ値を検出する
- 別の表を結合して特徴量を増やす

標準化は、Tribuo の `MeanStdDevTransformation` と突き合わせます。最後に、作った特徴量で線形回帰の決定係数がどう変わるかを実データで測ります。

[Python 版の第 9 章](../python/09-feature-engineering.md) と同じ TODO リストで進めます。Kotlin 版では、Kotlin DataFrame の列の型（`Int?` と `Double?`）、ライブラリごとに違う標準偏差の定義、文字コードを間違えたときの振る舞いの違いに注目してください。

## 9.2 題材とデータ

学習データの入手と配置は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。この章では 3 つのファイルを使います。

### Boston.csv

ボストン近郊の地域ごとの住宅価格のデータです。100 件、14 列あります。Kotlin DataFrame の `readCSV` で読み込んだときの型も並べます。

| 列 | 意味 | Kotlin DataFrame での型 | 欠損 |
|----|------|----------------------|------|
| CRIME | 犯罪率の水準（`high`・`low`・`very_low`） | `String` | なし |
| ZN・INDUS・AGE・DIS・B | 地域の環境を表す指標 | `Double` | なし |
| CHAS・TAX | 川沿いかどうか・税率 | `Int` | なし |
| NOX | 窒素酸化物の濃度 | `Double?` | 1 件 |
| RAD | 高速道路への近さ | `Int?` | 1 件 |
| RM | 住居あたりの平均部屋数 | `Double` | なし |
| PTRATIO | 生徒と教師の人数比 | `Double` | なし |
| LSTAT | 低所得者の割合（%） | `Double` | なし |
| PRICE | 住宅価格（目的変数） | `Double` | なし |

CRIME の件数は `very_low` が 50 件、`high` と `low` が 25 件ずつです。CRIME は数値ではないので、このままでは線形回帰に渡せません。

整数だけの列は `Int`、欠損のある整数の列は `Int?` として読み込まれることを覚えておいてください。9.9 節で、この型が問題になります。

### bike.tsv と weather.csv

`bike.tsv` は、ある自転車シェアサービスの日ごとの利用者数（`cnt`）と天気 ID（`weather_id`）などを記録した 731 件のデータです。区切り文字がカンマではなく **タブ** です。Kotlin DataFrame は、日付の列 `dteday` を `kotlinx.datetime.LocalDate` 型として読み込みます。

`weather.csv` は天気 ID と天気の名前（晴れ・曇り・雨）の対応表で、3 件です。文字コードが UTF-8 ではなく **Shift_JIS** です。

## 9.3 TODO リストの作成

**TODO リスト**:

- [ ] カテゴリ値をダミー変数にする
  - [ ] 先頭を除いたカテゴリを辞書順に求める
  - [ ] カテゴリごとに 0 と 1 の列を作る
  - [ ] カテゴリに無い値はすべての列を 0 にする
- [ ] 特徴量を標準化する
  - [ ] 訓練データから平均と標準偏差を求める
  - [ ] 訓練データの平均と標準偏差で別のデータを標準化する
  - [ ] Tribuo の `MeanStdDevTransformation` と突き合わせる
- [ ] 多項式特徴量を作る
  - [ ] 2 乗の項を加える
  - [ ] 2 つの列の積（交互作用の項）を加える
- [ ] IQR で外れ値を検出する
- [ ] タブ区切り・Shift_JIS のファイルを読み込んで結合する
- [ ] 特徴量の組み合わせごとに決定係数を測る

## 9.4 カテゴリ値をダミー変数にする

### カテゴリを求める

CRIME のような文字列の列は、カテゴリごとに「そのカテゴリなら 1、そうでなければ 0」の列に置き換えます。これを **ダミー変数** と呼びます。

カテゴリが 3 つなら、ダミー変数は 2 列で足ります。`low` でも `very_low` でもなければ `high` だと分かるからです。3 列すべて作ると、どれか 1 列が残りの列から計算できてしまい（多重共線性）、線形回帰の係数が求まらなくなります。そこで、辞書順で先頭のカテゴリを除きます。

```kotlin
// src/test/kotlin/chapter09/FeatureEngineeringTest.kt
class DummyCategoriesTest {
    @Test
    fun `先頭を除いたカテゴリを辞書順に返す`() {
        val crime = listOf("low", "high", "very_low", "low")

        assertEquals(listOf("low", "very_low"), dummyCategories(crime))
    }
}

class EncodeDummiesTest {
    @Test
    fun `カテゴリごとに0と1の列を作り元の列を取り除く`() {
        val df =
            dataFrameOf(
                "CRIME" to listOf("low", "high", "very_low"),
                "RM" to listOf(6.1, 5.2, 7.3),
            )

        val encoded = encodeDummies(df, "CRIME", listOf("low", "very_low"))

        assertEquals(listOf("RM", "CRIME_low", "CRIME_very_low"), encoded.columnNames())
        assertEquals(listOf(6.1, 5.2, 7.3), encoded["RM"].toList())
        assertEquals(listOf(1, 0, 0), encoded["CRIME_low"].toList())
        assertEquals(listOf(0, 0, 1), encoded["CRIME_very_low"].toList())
    }
}
```

```text
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:12:49 Unresolved reference 'dummyCategories'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:25:23 Unresolved reference 'encodeDummies'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:28:59 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:29:60 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:30:65 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
```

3 行目以降の `MatchGroup?` は、正規表現の一致結果の型です。`encodeDummies` が無いので `encoded` の型が決まらず、コンパイラは `encoded["RM"]` を、標準ライブラリにある別の `[]`（正規表現のグループを取り出す演算子）として解釈しようとしました。第 2 章でも見た、型が分からないことによる連鎖的なエラーです。

どちらもやることが明らかなので、明白な実装で進めます。

```kotlin
// src/main/kotlin/chapter09/FeatureEngineering.kt
fun dummyCategories(values: List<String?>): List<String> = values.filterNotNull().distinct().sorted().drop(1)

fun encodeDummies(
    df: AnyFrame,
    column: String,
    categories: List<String>,
): AnyFrame =
    categories.fold(df.remove(column)) { encoded, category ->
        val flags = df[column].values().map { if (it == category) 1 else 0 }
        encoded.add("${column}_$category") { flags[index()] }
    }
```

- 引数の型を `List<String?>` にしたのは、データフレームの列から取り出した値が null を含みうるからです。`filterNotNull()` で null を除き、`distinct()` で重複を除いてから並べ替えます
- `fold` は、元の列を取り除いたデータフレームから始めて、カテゴリごとに列を 1 つずつ足していきます
- `add("列名") { ... }` のブロックは行ごとに呼ばれ、`index()` でその行の位置を取り出せます

### カテゴリを引数で受け取る理由

`encodeDummies` がカテゴリの一覧を自分で求めずに引数で受け取るのは、訓練データとテストデータで **同じ列** を作るためです。テストデータに訓練データで見たことのない値が来ても、列は増えずにすべて 0 になるべきです。

```kotlin
    @Test
    fun `カテゴリに無い値はすべての列が0になる`() {
        val df = dataFrameOf("CRIME" to listOf("unknown"))

        val encoded = encodeDummies(df, "CRIME", listOf("low", "very_low"))

        assertEquals(listOf("CRIME_low", "CRIME_very_low"), encoded.columnNames())
        assertEquals(listOf(0), encoded["CRIME_low"].toList())
        assertEquals(listOf(0), encoded["CRIME_very_low"].toList())
    }
```

このテストは実装を変えずに通ります。Python 版では pandas の `get_dummies(drop_first=True)` と同じ結果になることを学習用テストで確かめましたが、Kotlin 版では自作の関数だけを使います。

## 9.5 特徴量を標準化する

### 標準化とは

Boston データの列は、単位も大きさもばらばらです。RM（部屋数）は 6 前後、TAX（税率）は数百です。**標準化** は、各列から平均を引いて標準偏差で割り、どの列も平均 0・標準偏差 1 にそろえる変換です。

$$z = \frac{x - \text{平均}}{\text{標準偏差}}$$

標準化で大事なのは、平均と標準偏差を **訓練データだけから求め**、その値でテストデータも変換することです。テストデータの平均を使うと、本来は未知であるはずのテストデータの情報がモデルの準備に漏れてしまいます。そこで、求める処理（`fit`）と変換する処理（`transform`）を分けた `Standardizer` を作ります。

### Red: 標準偏差の求め方

```kotlin
class StandardizerTest {
    @Test
    fun `訓練データから列ごとの平均と標準偏差を求める`() {
        val df =
            dataFrameOf(
                "RM" to listOf(1.0, 2.0, 3.0),
                "LSTAT" to listOf(10.0, 10.0, 40.0),
            )

        val standardizer = Standardizer.fit(df)

        assertEquals(2.0, standardizer.means.getValue("RM"), absoluteTolerance = 1e-12)
        assertEquals(20.0, standardizer.means.getValue("LSTAT"), absoluteTolerance = 1e-12)
        assertEquals(sqrt(2.0 / 3), standardizer.stds.getValue("RM"), absoluteTolerance = 1e-12)
        assertEquals(sqrt(200.0), standardizer.stds.getValue("LSTAT"), absoluteTolerance = 1e-12)
    }
}
```

```text
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:55:28 Unresolved reference 'Standardizer'.
```

RM の平均は 2 で、平均からの差の 2 乗は 1・0・1 です。その平均 2/3 の平方根が標準偏差です。Kotlin DataFrame の列にある `mean` と `std` を素直に使って実装してみます。

```kotlin
data class Standardizer(
    val means: Map<String, Double>,
    val stds: Map<String, Double>,
) {
    companion object {
        fun fit(df: AnyFrame): Standardizer =
            Standardizer(
                means = df.columnNames().associateWith { df[it].cast<Double>().mean() },
                stds = df.columnNames().associateWith { df[it].cast<Double>().std() },
            )
    }
}
```

- `companion object` の中の関数は、`Standardizer.fit(df)` のようにクラス名から呼べます。Python 版の `@classmethod` に当たる書き方で、「データから求めて作る」という生成の手段に名前を付けています
- `associateWith` は、リストの要素（列名）をキーにして、ブロックの結果を値にした `Map` を作ります

```text
StandardizerTest > 訓練データから列ごとの平均と標準偏差を求める() FAILED
    org.opentest4j.AssertionFailedError: Expected <0.816496580927726> with absolute tolerance <1.0E-12>, actual <1.0>.
4 tests completed, 1 failed
```

RM の標準偏差が 0.816 ではなく 1.0 になりました。Kotlin DataFrame の `std` は、引数 `ddof` の既定値が 1 で、差の 2 乗の合計を「件数 − 1」で割る **不偏標準偏差** を返すからです。pandas の `std()` と同じ既定値です。ここでは「件数」で割る **標準偏差**（`ddof = 0`）を使います。あわせて、整数の列にも使えるように `cast<Number>()` にしました。

```kotlin
                means = df.columnNames().associateWith { df[it].cast<Number>().mean() },
                stds = df.columnNames().associateWith { df[it].cast<Number>().std(ddof = 0) },
```

### 別のデータを標準化する

`transform` は、`fit` で求めた平均と標準偏差をそのまま使って変換します。テストでは、平均 2・標準偏差 0.5 の `Standardizer` を直接作って確かめます。data class なので、コンストラクターに値を渡すだけで作れます。

```kotlin
    @Test
    fun `訓練データの平均と標準偏差で別のデータを標準化する`() {
        val standardizer = Standardizer(means = mapOf("RM" to 2.0), stds = mapOf("RM" to 0.5))
        val other = dataFrameOf("RM" to listOf(1.0, 2.0, 4.0))

        val standardized = standardizer.transform(other)

        assertEquals(listOf(-2.0, 0.0, 4.0), standardized["RM"].toList())
    }
```

```text
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:68:41 Unresolved reference 'transform' on receiver of type 'Standardizer'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:70:65 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
```

```kotlin
    fun transform(df: AnyFrame): AnyFrame =
        means.keys.fold(df) { standardized, column ->
            standardized.convert(column).with { ((it as Number).toDouble() - means.getValue(column)) / stds.getValue(column) }
        }
```

`convert(列名).with { ... }` は、列の値を変換した新しいデータフレームを返します。第 2 章で使った `fillNulls(...).with` と違い、`convert` は列の型を変えられるので、`Int` の列も `Double` の列に変換できます。

### すべて同じ値の列

CHAS（川沿いかどうか）のように 0 と 1 しかない列は、訓練データの取り方によってはすべて同じ値になります。すると標準偏差が 0 になり、0 での割り算が起きます。

```kotlin
    @Test
    fun `すべて同じ値の列は0にする`() {
        val df = dataFrameOf("CHAS" to listOf(1.0, 1.0, 1.0))

        val standardized = Standardizer.fit(df).transform(df)

        assertEquals(listOf(0.0, 0.0, 0.0), standardized["CHAS"].toList())
    }
```

```text
StandardizerTest > 訓練データから列ごとの平均と標準偏差を求める() PASSED
StandardizerTest > すべて同じ値の列は0にする() FAILED
    org.opentest4j.AssertionFailedError: expected: <[0.0, 0.0, 0.0]> but was: <[NaN, NaN, NaN]>
StandardizerTest > 訓練データの平均と標準偏差で別のデータを標準化する() PASSED
6 tests completed, 1 failed
```

`Double` の 0.0 を 0.0 で割ると、例外にならずに NaN（非数）になります。整数の `0 / 0` は `ArithmeticException` を投げるのと対照的です。黙って NaN が混ざると、後の学習で原因の分かりにくい結果になります。標準偏差が 0 のときは 1 で割るようにします。

```kotlin
                stds = df.columnNames().associateWith { df[it].cast<Number>().std(ddof = 0).takeIf { std -> std != 0.0 } ?: 1.0 },
```

`takeIf { 条件 }` は、条件を満たせば値をそのまま、満たさなければ null を返します。エルビス演算子 `?:` と組み合わせると、「0 でなければその値、0 なら 1.0」を 1 つの式で書けます。

### Tribuo の標準化と突き合わせる

Tribuo には、特徴量を標準化する `MeanStdDevTransformation` があります。統計量を集める `TransformStatistics` に値を 1 つずつ観測させ、そこから変換器（`Transformer`）を作る、という使い方をします。

```kotlin
private fun tribuoStandardize(
    train: List<Double>,
    values: List<Double>,
): List<Double> {
    val statistics = MeanStdDevTransformation().createStats()
    train.forEach(statistics::observeValue)
    val transformer = statistics.generateTransformer()
    return values.map(transformer::transform)
}
```

`statistics::observeValue` と `transformer::transform` は、特定のオブジェクトのメソッドを関数として渡す **束縛された関数参照** です。`forEach { statistics.observeValue(it) }` と同じ意味です。

最初は、自作の `Standardizer` と同じ値になると考えて、次のテストを書きました。

```kotlin
class TribuoStandardizationTest {
    @Test
    fun `TribuoのMeanStdDevTransformationと同じ値になる`() {
        val train = listOf(5.5, 6.0, 7.5, 6.5)
        val test = listOf(6.2, 8.0)

        val standardized = Standardizer.fit(dataFrameOf("RM" to train)).transform(dataFrameOf("RM" to test))

        val expected = tribuoStandardize(train, test)
        standardized["RM"].toList().zip(expected).forEach { (actual, tribuo) ->
            assertEquals(tribuo, actual as Double, absoluteTolerance = 1e-12)
        }
    }
}
```

```text
TribuoStandardizationTest > TribuoのMeanStdDevTransformationと同じ値になる() FAILED
    org.opentest4j.AssertionFailedError: Expected <-0.20493901531919173> with absolute tolerance <1.0E-12>, actual <-0.2366431913239844>.
7 tests completed, 1 failed
```

値が一致しません。比を取ると 0.2049 ÷ 0.2366 ≒ 0.866 で、これは √(3/4) です。訓練データは 4 件なので、「件数 − 1」と「件数」の比の平方根に当たります。Tribuo は、自作とは違って不偏標準偏差で割っていると考えられます。

一致を前提にしたテストを、確かめた事実を記録するテストに書き直しました。

```kotlin
private fun assertValues(
    expected: List<Double>,
    actual: List<Double>,
) {
    assertEquals(expected.size, actual.size)
    expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, absoluteTolerance = 1e-12) }
}

class TribuoStandardizationTest {
    private val train = listOf(5.5, 6.0, 7.5, 6.5)
    private val test = listOf(6.2, 8.0)

    @Test
    fun `TribuoのMeanStdDevTransformationは件数から1を引いて割る標準偏差を使う`() {
        val mean = train.average()
        val sampleStd = dataFrameOf("RM" to train)["RM"].cast<Double>().std(ddof = 1)

        assertValues(test.map { (it - mean) / sampleStd }, tribuoStandardize(train, test))
    }

    @Test
    fun `自作の標準化に件数から決まる係数を掛けるとTribuoの値になる`() {
        val standardized = Standardizer.fit(dataFrameOf("RM" to train)).transform(dataFrameOf("RM" to test))
        val ratio = sqrt((train.size - 1.0) / train.size)

        assertValues(tribuoStandardize(train, test), standardized["RM"].toList().map { (it as Double) * ratio })
    }
}
```

どちらのテストも通りました。

| ライブラリ | 標準偏差の既定 |
|-----------|--------------|
| 自作の `Standardizer`（scikit-learn の `StandardScaler` と同じ） | 件数で割る（`ddof = 0`） |
| Kotlin DataFrame の `std`・pandas の `std` | 件数 − 1 で割る（`ddof = 1`） |
| Tribuo の `MeanStdDevTransformation` | 件数 − 1 で割る |

件数が十分に多ければ 2 つの差は小さくなりますが、Boston の訓練データ 70 件では約 0.7% の差になります。どちらが正しいというものではありません。ライブラリを置き換えるときは、同じ名前の処理でも定義が同じとは限らないので、小さなデータで値を突き合わせてから使います。

## 9.6 多項式特徴量を作る

### 2 乗の項

線形回帰は「特徴量 × 係数」の足し算でしか予測できません。価格が部屋数の 2 乗に比例して増えるような曲線の関係は、部屋数の列だけでは表せません。そこで、部屋数の 2 乗の列を特徴量として加えます。モデルは線形のままでも、曲線の関係を表せるようになります。

```kotlin
class PolynomialFeaturesTest {
    @Test
    fun `1列なら元の列と2乗の列を返す`() {
        val df = dataFrameOf("RM" to listOf(2.0, 3.0))

        val features = polynomialFeatures(df, listOf("RM"))

        assertEquals(listOf("RM", "RM^2"), features.columnNames())
        assertEquals(listOf(2.0, 3.0), features["RM"].toList())
        assertEquals(listOf(4.0, 9.0), features["RM^2"].toList())
    }
}
```

```text
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:130:24 Unresolved reference 'polynomialFeatures'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:133:55 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:134:57 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
```

2 乗の列を加えるだけの実装から始めます。

```kotlin
private fun AnyFrame.doubles(column: String): List<Double> = this[column].values().map { (it as Number).toDouble() }

fun polynomialFeatures(
    df: AnyFrame,
    columns: List<String>,
): AnyFrame =
    columns.fold(df.select(*columns.toTypedArray())) { features, column ->
        val values = df.doubles(column)
        features.add("$column^2") { values[index()] * values[index()] }
    }
```

`select` は可変長引数（`vararg`）で列名を受け取ります。`*` は **スプレッド演算子** で、配列の要素を可変長引数に展開します。この書き方は 9.11 節で見直します。

### 三角測量: 交互作用の項

2 列を渡したときは、2 乗の項に加えて、2 つの列の積（**交互作用の項**）も作ります。「部屋数が多く、かつ低所得者の割合が低い」ような組み合わせの効果を表すためです。列の並び順も確かめます。

```kotlin
    @Test
    fun `2列なら2乗の列と2つの列の積の列を加える`() {
        val df =
            dataFrameOf(
                "RM" to listOf(2.0, 3.0),
                "LSTAT" to listOf(5.0, 7.0),
            )

        val features = polynomialFeatures(df, listOf("RM", "LSTAT"))

        assertEquals(listOf("RM", "LSTAT", "RM^2", "RM LSTAT", "LSTAT^2"), features.columnNames())
        assertEquals(listOf(4.0, 9.0), features["RM^2"].toList())
        assertEquals(listOf(10.0, 21.0), features["RM LSTAT"].toList())
        assertEquals(listOf(25.0, 49.0), features["LSTAT^2"].toList())
    }
```

```text
PolynomialFeaturesTest > 1列なら元の列と2乗の列を返す() PASSED
PolynomialFeaturesTest > 2列なら2乗の列と2つの列の積の列を加える() FAILED
    org.opentest4j.AssertionFailedError: expected: <[RM, LSTAT, RM^2, RM LSTAT, LSTAT^2]> but was: <[RM, LSTAT, RM^2, LSTAT^2]>
10 tests completed, 1 failed
```

2 乗の項と交互作用の項は、「列の組を、同じ列を 2 回選ぶことも許して選ぶ」ことで一度に作れます。Python 版では標準ライブラリの `itertools.combinations_with_replacement` を使いましたが、Kotlin の標準ライブラリには無いので、ジェネリック関数として自作します。

```kotlin
fun polynomialFeatures(
    df: AnyFrame,
    columns: List<String>,
): AnyFrame =
    pairsWithReplacement(columns).fold(df.select(*columns.toTypedArray())) { features, (left, right) ->
        val leftValues = df.doubles(left)
        val rightValues = df.doubles(right)
        features.add(termName(left, right)) { leftValues[index()] * rightValues[index()] }
    }

fun <T> pairsWithReplacement(items: List<T>): List<Pair<T, T>> =
    items.indices.flatMap { i -> (i until items.size).map { j -> items[i] to items[j] } }

fun termName(
    left: String,
    right: String,
): String = if (left == right) "$left^2" else "$left $right"
```

- `pairsWithReplacement` は、`i ≤ j` となる添字の組をすべて作ります。`["RM", "LSTAT"]` なら `(RM, RM)`・`(RM, LSTAT)`・`(LSTAT, LSTAT)` の順です。`<T>` の型引数を持たせたので、文字列以外のリストにも使えます
- `flatMap` は、各要素から作ったリストを 1 つのリストにつなげます
- `fold` のラムダの引数 `(left, right)` は、`Pair` を 2 つの変数に分解して受け取る書き方です

列名を `RM^2`・`RM LSTAT` という形にしたのは、Python 版で突き合わせた scikit-learn の `PolynomialFeatures` の `get_feature_names_out()` と同じ名前にするためです。3 列のときの並びも確かめておきます。

```kotlin
    @Test
    fun `3列ならscikit_learnのPolynomialFeaturesと同じ並びで9列を作る`() {
        val df =
            dataFrameOf(
                "RM" to listOf(5.5, 6.0),
                "LSTAT" to listOf(12.0, 4.0),
                "PTRATIO" to listOf(18.0, 15.0),
            )

        val features = polynomialFeatures(df, listOf("RM", "LSTAT", "PTRATIO"))

        assertEquals(
            listOf("RM", "LSTAT", "PTRATIO", "RM^2", "RM LSTAT", "RM PTRATIO", "LSTAT^2", "LSTAT PTRATIO", "PTRATIO^2"),
            features.columnNames(),
        )
    }
```

3 列から作られる列は、元の 3 列・2 乗の 3 列・交互作用の 3 列の合計 9 列です。列の数は元の列数の 2 乗に近い速さで増えるので、むやみに作ると学習データの件数に対して特徴量が多くなりすぎます。この影響は 9.9 節で実測します。

## 9.7 外れ値を検出する

他の値から大きく離れた値を **外れ値** と呼びます。ここでは、箱ひげ図でも使われる **IQR（四分位範囲）** による方法を実装します。値を小さい順に並べて 25% の位置の値を第 1 四分位数（Q1）、75% の位置の値を第 3 四分位数（Q3）とし、その差 IQR = Q3 − Q1 を求めます。Q1 − 1.5 × IQR より小さい値と、Q3 + 1.5 × IQR より大きい値を外れ値とみなします。

まず上側の外れ値のテストを書きます。`[1, 2, 3, 4, 100]` なら Q1 = 2、Q3 = 4、IQR = 2 なので、4 + 1.5 × 2 = 7 を超える 100 が外れ値です。

```kotlin
class IqrOutliersTest {
    @Test
    fun `第3四分位数からIQRの15倍より大きい値を外れ値とする`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 100.0)

        assertEquals(listOf(false, false, false, false, true), iqrOutliers(values))
    }
}
```

```text
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:176:64 Unresolved reference 'iqrOutliers'.
```

Kotlin の標準ライブラリには四分位数を求める関数が無いので、pandas の `quantile` の既定と同じく、位置が値と値の間に来たら前後の値から線形補間する `quantile` を作ります。

```kotlin
fun quantile(
    values: List<Double>,
    q: Double,
): Double {
    val sorted = values.sorted()
    val position = (sorted.size - 1) * q
    val lower = floor(position).toInt()
    val upper = ceil(position).toInt()
    return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
}

fun iqrOutliers(
    values: List<Double>,
    k: Double = 1.5,
): List<Boolean> {
    val q1 = quantile(values, 0.25)
    val q3 = quantile(values, 0.75)
    return values.map { it > q3 + k * (q3 - q1) }
}
```

`[1, 2, 3, 4, 100]` の 25% の位置は (5 − 1) × 0.25 = 1 番目（0 始まり）で、値 2 がちょうどその位置にあります。値の間に位置が来る場合は、次のテストで確かめています。

```kotlin
class QuantileTest {
    @Test
    fun `四分位数の位置が値の間にあれば前後の値から線形補間する`() {
        assertEquals(1.75, quantile(listOf(4.0, 1.0, 3.0, 2.0), 0.25), absoluteTolerance = 1e-12)
    }
}
```

下側の外れ値のテストで三角測量します。

```kotlin
    @Test
    fun `第1四分位数からIQRの15倍より小さい値も外れ値とする`() {
        val values = listOf(-100.0, 1.0, 2.0, 3.0, 4.0)

        assertEquals(listOf(true, false, false, false, false), iqrOutliers(values))
    }
```

```text
IqrOutliersTest > 第1四分位数からIQRの15倍より小さい値も外れ値とする() FAILED
    org.opentest4j.AssertionFailedError: expected: <[true, false, false, false, false]> but was: <[false, false, false, false, false]>
IqrOutliersTest > 第3四分位数からIQRの15倍より大きい値を外れ値とする() PASSED
13 tests completed, 1 failed
```

```kotlin
    val q3 = quantile(values, 0.75)
    val iqr = q3 - q1
    return values.map { it < q1 - k * iqr || it > q3 + k * iqr }
```

戻り値は、元の値と同じ順の `List<Boolean>` です。Python 版の真偽値の Series に当たり、どの行が外れ値かを位置で対応させて使います。

## 9.8 表を結合して特徴量を増やす

### タブ区切りと Shift_JIS

自転車の利用者数の表には天気 ID しかなく、それが晴れなのか雨なのかは別の表にあります。2 つの表を天気 ID で **結合** すれば、天気の名前を特徴量として使えます。まず読み込みです。

```kotlin
class LoadBikeAndWeatherTest {
    private val directory: Path = createTempDirectory()

    private fun writeFile(
        name: String,
        text: String,
        charset: Charset = Charsets.UTF_8,
    ): File = File(directory.toFile(), name).apply { writeText(text, charset) }

    @Test
    fun `タブ区切りのファイルを読み込む`() {
        val tsvFile = writeFile("bike.tsv", "dteday\tweather_id\tcnt\n2030-04-01\t1\t120\n")

        val bike = loadBike(tsvFile)

        assertEquals(listOf("dteday", "weather_id", "cnt"), bike.columnNames())
        assertEquals(listOf(1), bike["weather_id"].toList())
        assertEquals(listOf(120), bike["cnt"].toList())
    }

    @Test
    fun `Shift_JISのファイルを読み込む`() {
        val csvFile = writeFile("weather.csv", "weather_id,weather\n1,晴れ\n", Charset.forName("Shift_JIS"))

        val weather = loadWeather(csvFile)

        assertEquals(listOf(1), weather["weather_id"].toList())
        assertEquals(listOf("晴れ"), weather["weather"].toList())
    }
}
```

`writeFile` の引数 `charset` には既定値 `Charsets.UTF_8` を付けたので、UTF-8 のファイルは文字コードを省略して書けます。（この `writeFile` は 9.11 節の整理で導入したもので、最初は各テストで `File(...).apply { writeText(...) }` と書いていました。）

```text
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:205:20 Unresolved reference 'loadBike'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:208:52 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:209:47 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:216:23 Unresolved reference 'loadWeather'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:218:55 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:219:55 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
```

タブ区切りは、`readCSV` の名前付き引数 `delimiter` で指定します。天気の表は、まず文字コードを指定せずに読み込んでみます。

```kotlin
fun loadBike(tsvFile: File): AnyFrame = DataFrame.readCSV(tsvFile, delimiter = '\t')

fun loadWeather(csvFile: File): AnyFrame = DataFrame.readCSV(csvFile)
```

```text
LoadBikeAndWeatherTest > タブ区切りのファイルを読み込む() PASSED
LoadBikeAndWeatherTest > Shift_JISのファイルを読み込む() FAILED
    org.opentest4j.AssertionFailedError: expected: <[晴れ]> but was: <[����]>
16 tests completed, 1 failed
```

Python 版では、pandas が `UnicodeDecodeError` という例外で失敗しました。Kotlin DataFrame（JVM の文字コード変換）は例外を投げず、UTF-8 として解釈できないバイトを置換文字 `�`（U+FFFD）に置き換えて読み込みを続けます。**エラーにならずに文字化けしたデータが入ってくる** ので、テストが無ければ気付くのが遅れます。テストのデータを実データと同じ Shift_JIS で書き出していたので、この問題を捕まえられました。文字コードを指定します。

```kotlin
fun loadWeather(csvFile: File): AnyFrame = DataFrame.readCSV(csvFile, charset = Charset.forName("Shift_JIS"))
```

`Charsets` には UTF-8 などよく使う文字コードだけが用意されているので、Shift_JIS は `Charset.forName` で名前から取り出します。

### 結合と集計

```kotlin
class JoinWeatherTest {
    @Test
    fun `天気IDで天気の名前を結合する`() {
        val bike = dataFrameOf("weather_id" to listOf(2, 1), "cnt" to listOf(80, 120))
        val weather = dataFrameOf("weather_id" to listOf(1, 2), "weather" to listOf("晴れ", "曇り"))

        val joined = joinWeather(bike, weather)

        assertEquals(listOf("weather_id", "cnt", "weather"), joined.columnNames())
        assertEquals(listOf(80, 120), joined["cnt"].toList())
        assertEquals(listOf("曇り", "晴れ"), joined["weather"].toList())
    }

    @Test
    fun `天気の表に無い天気IDの行は残さない`() {
        val bike = dataFrameOf("weather_id" to listOf(1, 9), "cnt" to listOf(120, 30))
        val weather = dataFrameOf("weather_id" to listOf(1), "weather" to listOf("晴れ"))

        val joined = joinWeather(bike, weather)

        assertEquals(listOf(120), joined["cnt"].toList())
    }
}

class MeanCountByWeatherTest {
    @Test
    fun `天気ごとの平均利用者数を多い順に求める`() {
        val joined = dataFrameOf("weather" to listOf("雨", "晴れ", "晴れ"), "cnt" to listOf(20, 100, 140))

        assertEquals(listOf("晴れ" to 120.0, "雨" to 20.0), meanCountByWeather(joined).toList())
    }
}
```

```text
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:229:22 Unresolved reference 'joinWeather'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:232:53 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:233:60 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:241:22 Unresolved reference 'joinWeather'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:243:49 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:252:58 Unresolved reference 'meanCountByWeather'.
```

```kotlin
fun joinWeather(
    bike: AnyFrame,
    weather: AnyFrame,
): AnyFrame = bike.innerJoin(weather, "weather_id")

fun meanCountByWeather(joined: AnyFrame): Map<String, Double> =
    joined
        .groupBy("weather")
        .mean("cnt")
        .sortByDesc("cnt")
        .rows()
        .associate { it["weather"] as String to (it["cnt"] as Number).toDouble() }
```

- `innerJoin(他の表, "列名")` は **内部結合** で、両方の表にある天気 ID の行だけを残します。2 つ目のテストのように、天気の表に無い ID の行は消えます。行を消したくない場合は `leftJoin` を使い、天気の名前を null にします。どちらを選ぶかは、結合の前後で件数が変わってよいかどうかで決めます。実データでは、結合の前後とも 731 件でした
- `groupBy("weather").mean("cnt")` は、天気ごとの `cnt` の平均を 1 行ずつ持つデータフレームを返します。`sortByDesc` で平均の大きい順に並べます
- `associate` は、各行から `キー to 値` の組を作って `Map` にします。作られる `Map` は要素を入れた順を保つ（`LinkedHashMap`）ので、平均の大きい順が残ります。テストでは `toList()` で順番まで比べています

## 9.9 特徴量の効果を測る

### Boston データの前処理

ここまでの部品と、第 2 章の分割・欠損値補完の関数を組み合わせて、Boston データを前処理します。価格は数値なので、第 2 章で型引数を持たせた `TrainTestSplit<Double>` で表します。

```kotlin
class PrepareBostonTest {
    private val directory: Path = createTempDirectory()

    @Test
    fun `ダミー変数化と欠損値の補完をして特徴量と価格に分ける`() {
        val csvFile =
            File(directory.toFile(), "boston.csv").apply {
                writeText(
                    "CRIME,RM,NOX,PRICE\n" +
                        "low,6.0,,20.0\n" +
                        "high,5.0,0.5,15.0\n" +
                        "very_low,7.0,0.4,30.0\n" +
                        "low,6.5,0.6,25.0\n",
                )
            }

        val split = prepareBoston(csvFile, testSize = 0.5, seed = 0)

        val columns = listOf("RM", "NOX", "CRIME_low", "CRIME_very_low")
        assertEquals(columns, split.xTrain.columnNames())
        assertEquals(columns, split.xTest.columnNames())
        assertEquals(0, countMissing(split.xTrain).values.sum())
        assertEquals(0, countMissing(split.xTest).values.sum())
        assertEquals(2 to 2, split.tTrain.size to split.tTest.size)
    }
}
```

```text
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:273:21 Unresolved reference 'prepareBoston'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:280:9 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:280:9 Inapplicable candidate(s): fun <T> assertEquals(expected: T, actual: T, message: String? = ...): Unit
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:280:48 Cannot infer type for type parameter 'A'. Specify it explicitly.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:280:48 Cannot infer type for type parameter 'B'. Specify it explicitly.
```

```kotlin
const val TARGET = "PRICE"

fun prepareBoston(
    csvFile: File,
    testSize: Double,
    seed: Int,
): TrainTestSplit<Double> {
    val df = DataFrame.readCSV(csvFile)
    val encoded = encodeDummies(df, "CRIME", dummyCategories(df["CRIME"].values().map { it as String? }))
    val x = encoded.remove(TARGET)
    val split = splitTrainTest(x, encoded.doubles(TARGET), testSize, seed)
    val means = columnMeans(split.xTrain, x.columnNames())
    return split.copy(
        xTrain = fillMissing(split.xTrain, means),
        xTest = fillMissing(split.xTest, means),
    )
}
```

欠損値を埋める平均は訓練データだけから求めますが、ダミー変数のカテゴリの一覧はデータ全体から求めています。カテゴリの一覧は「CRIME がどんな値を取りうるか」というデータの定義で、価格の情報を含まないからです。訓練データだけから求めると、4 件のテストデータのように訓練データに現れなかったカテゴリの列が作られず、テストデータと列がそろわなくなります。

### 実データで見つかった型の問題

このテストは通りましたが、実データで `main`（後述）を動かすと、次の例外で失敗しました。

```text
Exception in thread "main" java.lang.IllegalArgumentException: Can not add value of class kotlin.Double to column of type kotlin.Int?. Value = 8.710144927536232
	at org.jetbrains.kotlinx.dataframe.impl.TypedColumnDataCollector.add(ColumnDataCollector.kt:67)
	...
	at chapter02.IrisPreprocessingKt.fillMissing(IrisPreprocessing.kt:35)
	at chapter09.FeatureEngineeringKt.prepareBoston(FeatureEngineering.kt:130)
```

9.2 節の表のとおり、RAD は欠損のある整数の列で、`Int?` 型として読み込まれます。第 2 章の `fillMissing` は、そこに平均値の `Double`（8.71…）を入れようとして失敗しました。iris のデータは欠損のある列がすべて `Double?` だったので、第 2 章では起きなかった問題です。架空のデータで同じ状況を作るテストを書きます。

```kotlin
    @Test
    fun `整数の列に欠損値があっても平均値で補完する`() {
        val csvFile =
            File(directory.toFile(), "boston.csv").apply {
                writeText(
                    "CRIME,RAD,PRICE\n" +
                        "low,1,20.0\n" +
                        "high,,15.0\n" +
                        "very_low,4,30.0\n" +
                        "low,2,25.0\n",
                )
            }

        val split = prepareBoston(csvFile, testSize = 0.5, seed = 0)

        assertEquals(0, countMissing(split.xTrain).values.sum())
        assertEquals(0, countMissing(split.xTest).values.sum())
    }
```

```text
PrepareBostonTest > 整数の列に欠損値があっても平均値で補完する() FAILED
    java.lang.IllegalArgumentException: Can not add value of class kotlin.Double to column of type kotlin.Int?. Value = 2.0
PrepareBostonTest > ダミー変数化と欠損値の補完をして特徴量と価格に分ける() PASSED
25 tests completed, 1 failed
```

線形回帰に渡す特徴量はどうせ小数として扱うので、分割の前に特徴量の列をすべて `Double?` にそろえます。

```kotlin
// 欠損値のある整数の列（Int?）には平均値（Double）を入れられないので、特徴量をすべて Double? にそろえる
private fun AnyFrame.withDoubleColumns(): AnyFrame =
    columnNames().fold(this) { df, name -> df.convert(name).with { (it as Number?)?.toDouble() } }
```

```kotlin
    val x = encoded.remove(TARGET).withDoubleColumns()
```

`(it as Number?)?.toDouble()` は、値が null ならそのまま null、数値なら `Double` に変換します。欠損は null のまま残るので、その後の `fillMissing` で平均値に置き換わります。

pandas は列に `NaN` が混ざると整数の列を自動で小数の列にするので、Python 版ではこの問題は起きませんでした。Kotlin DataFrame は列の型を静的に保とうとするので、型の食い違いが実行時の例外として表に出ます。架空の値の単体テストだけでなく、実データでも動かしてみることの大切さが分かります。

### 特徴量の組み合わせごとに決定係数を測る

多項式特徴量の中から使う列（`terms`）を選び、標準化してから線形回帰で学習し、訓練データとテストデータの決定係数を返す関数を作ります。

テストでは、価格が部屋数の 2 次式（3 × RM² + 1）になっている架空のデータを使います。

```kotlin
private fun quadraticSplit(): TrainTestSplit<Double> {
    fun price(rm: Double): Double = 3 * rm * rm + 1

    val trainRm = listOf(1.0, 2.0, 3.0, 4.0)
    val testRm = listOf(5.0, 6.0)
    return TrainTestSplit(
        xTrain = dataFrameOf("RM" to trainRm, "LSTAT" to listOf(9.0, 7.0, 8.0, 6.0)),
        xTest = dataFrameOf("RM" to testRm, "LSTAT" to listOf(5.0, 4.0)),
        tTrain = trainRm.map(::price),
        tTest = testRm.map(::price),
    )
}

class ScoreFeatureSetTest {
    @Test
    fun `2乗の項が無いと2次式の価格を当てきれない`() {
        val (trainScore, _) = scoreFeatureSet(quadraticSplit(), listOf("RM"), listOf("RM"))

        assertTrue(trainScore < 1.0)
    }

    @Test
    fun `2乗の項を加えると2次式の価格を当てられる`() {
        val (trainScore, testScore) = scoreFeatureSet(quadraticSplit(), listOf("RM"), listOf("RM", "RM^2"))

        assertEquals(1.0, trainScore, absoluteTolerance = 1e-9)
        assertEquals(1.0, testScore, absoluteTolerance = 1e-9)
    }
}
```

- 関数の中に関数（`price`）を定義できます（ローカル関数）。`trainRm.map(::price)` のように、関数参照として渡せます
- `val (trainScore, _) = ...` は、`Pair` を分解して受け取ります。使わない側は `_` にします

```text
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:302:31 Unresolved reference 'scoreFeatureSet'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:302:31 Operator call 'component1()' is ambiguous for destructuring of type '??? (Unresolved name: scoreFeatureSet)'. Applicable candidates:
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:302:31 Operator call 'component2()' is ambiguous for destructuring of type '??? (Unresolved name: scoreFeatureSet)'. Applicable candidates:
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:304:31 'operator' modifier is required on 'fun <T> Comparable<T>.compareTo(other: T): Int'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:309:39 Unresolved reference 'scoreFeatureSet'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:309:39 Operator call 'component1()' is ambiguous for destructuring of type '??? (Unresolved name: scoreFeatureSet)'. Applicable candidates:
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:309:39 Operator call 'component2()' is ambiguous for destructuring of type '??? (Unresolved name: scoreFeatureSet)'. Applicable candidates:
```

分解（`component1()`・`component2()`）や比較（`compareTo`）のエラーも、`scoreFeatureSet` の戻り値の型が分からないことから連鎖したものです。

線形回帰は、切片と係数をまとめたベクトル β について、**正規方程式** XᵀX β = Xᵀt を解いて求めます（X は先頭に 1 の列を足した特徴量の行列、t は正解の価格）。行列の計算そのものを自作するのは第 7 章の主題なので、この章では特徴量の効果を測ることに集中し、行列の計算は Tribuo の行列ライブラリ（tribuo-math）の `DenseMatrix` に任せます。決定係数 R² = 1 − 残差の 2 乗和 ÷ 平均からの差の 2 乗和も求めます。

```kotlin
private fun AnyFrame.toRows(): Array<DoubleArray> =
    Array(rowsCount()) { row -> DoubleArray(columnsCount()) { column -> (columns()[column][row] as Number).toDouble() } }

data class LinearModel(
    val intercept: Double,
    val weights: List<Double>,
) {
    fun predict(rows: Array<DoubleArray>): List<Double> = rows.map { row -> intercept + row.indices.sumOf { weights[it] * row[it] } }
}

fun fitLinearRegression(
    rows: Array<DoubleArray>,
    t: List<Double>,
): LinearModel {
    val design = DenseMatrix.createDenseMatrix(Array(rows.size) { doubleArrayOf(1.0) + rows[it] })
    val transposed = design.transpose()
    val cholesky =
        transposed.matrixMultiply(design).choleskyFactorization().orElseThrow {
            IllegalArgumentException("特徴量の列が互いに独立でないため、正規方程式を解けません")
        }
    val beta = cholesky.solve(transposed.leftMultiply(DenseVector.createDenseVector(t.toDoubleArray()))).toArray()
    return LinearModel(intercept = beta.first(), weights = beta.drop(1))
}

fun rSquared(
    actual: List<Double>,
    predicted: List<Double>,
): Double {
    val mean = actual.average()
    val residual = actual.zip(predicted).sumOf { (a, p) -> (a - p) * (a - p) }
    val total = actual.sumOf { (it - mean) * (it - mean) }
    return 1 - residual / total
}

fun scoreFeatureSet(
    split: TrainTestSplit<Double>,
    columns: List<String>,
    terms: List<String>,
): Pair<Double, Double> {
    val train = polynomialFeatures(split.xTrain, columns).select(*terms.toTypedArray())
    val test = polynomialFeatures(split.xTest, columns).select(*terms.toTypedArray())
    val standardizer = Standardizer.fit(train)
    val xTrain = standardizer.transform(train).toRows()
    val xTest = standardizer.transform(test).toRows()
    val model = fitLinearRegression(xTrain, split.tTrain)
    return rSquared(split.tTrain, model.predict(xTrain)) to rSquared(split.tTest, model.predict(xTest))
}
```

- `doubleArrayOf(1.0) + rows[it]` は、行の先頭に切片のための 1 を足した配列を作ります
- XᵀX は対称で、列が互いに独立なら正定値なので、**コレスキー分解** で解けます。`choleskyFactorization()` は分解できないときに空になる `Optional` を返すので、`orElseThrow` で理由の分かる例外にしています
- `leftMultiply` は、行列にベクトルを右から掛けた積（Xᵀt）を返します
- `Pair` を返す関数は `a to b` で値を作れます

```text
ScoreFeatureSetTest > 2乗の項が無いと2次式の価格を当てきれない() PASSED
ScoreFeatureSetTest > 2乗の項を加えると2次式の価格を当てられる() PASSED
BUILD SUCCESSFUL in 29s
```

RM だけでは直線しか引けないので決定係数は 1 に届きません。RM² を加えると、訓練データにも、学習に使っていない RM = 5・6 のテストデータにも完全に当てはまります。

2 つのテストだけでは、切片と係数そのものが正しいかは確かめていません。答えが分かっている式（t = 2 + 3x₁ − x₂）で、係数も確かめておきます。

```kotlin
class FitLinearRegressionTest {
    @Test
    fun `正規方程式を解いて切片と係数を求める`() {
        val rows = arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0), doubleArrayOf(1.0, 1.0), doubleArrayOf(2.0, 3.0))
        val t = rows.map { 2 + 3 * it[0] - it[1] }

        val model = fitLinearRegression(rows, t)

        assertEquals(2.0, model.intercept, absoluteTolerance = 1e-9)
        assertValues(listOf(3.0, -1.0), model.weights)
    }
}
```

### 外れ値を除いて学習する

外れ値の影響も測れるように、訓練データから価格が外れ値の行を除く関数を作ります。テストデータは実際に予測する対象なので、除きません。

```kotlin
class RemoveTargetOutliersTest {
    @Test
    fun `訓練データから価格が外れ値の行を取り除きテストデータは残す`() {
        val split =
            TrainTestSplit(
                xTrain = dataFrameOf("RM" to listOf(5.0, 6.0, 6.5, 7.0, 8.0)),
                xTest = dataFrameOf("RM" to listOf(9.0)),
                tTrain = listOf(1.0, 2.0, 3.0, 4.0, 100.0),
                tTest = listOf(500.0),
            )

        val removed = removeTargetOutliers(split)

        assertEquals(listOf(5.0, 6.0, 6.5, 7.0), removed.xTrain["RM"].toList())
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), removed.tTrain)
        assertEquals(listOf(500.0), removed.tTest)
    }
}
```

```text
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:340:23 Unresolved reference 'removeTargetOutliers'.
e: .../src/test/kotlin/chapter09/FeatureEngineeringTest.kt:342:71 Unresolved reference 'toList' on receiver of type 'MatchGroup?'.
```

```kotlin
fun removeTargetOutliers(split: TrainTestSplit<Double>): TrainTestSplit<Double> {
    val outliers = iqrOutliers(split.tTrain)
    val keep = outliers.indices.filterNot { outliers[it] }
    return split.copy(xTrain = split.xTrain[keep], tTrain = split.tTrain.slice(keep))
}
```

「外れ値でない行の位置」のリストを作り、第 2 章と同じく `xTrain[keep]` と `tTrain.slice(keep)` で特徴量と価格の対応を保ったまま取り出します。data class の `copy` で、テストデータはそのまま残します。

### 実データで測る

特徴量には RM・LSTAT・PTRATIO を使います。Kotlin 版の訓練データで各列と価格の相関係数を求めると（9.10 節の Notebook）、絶対値の大きい順に LSTAT（−0.692）・RM（0.654）・PTRATIO（−0.370）となり、4 番目の INDUS（−0.302）以下より大きいからです。Python 版とは訓練データに入った行が違うので値は異なりますが、上位 3 列は同じでした。

```kotlin
// src/main/kotlin/chapter09/Main.kt
val COLUMNS = listOf("RM", "LSTAT", "PTRATIO")
val SQUARES = listOf("RM^2", "LSTAT^2", "PTRATIO^2")
val FEATURE_SETS =
    linkedMapOf(
        "元の特徴量" to COLUMNS,
        "2 乗の項を追加" to COLUMNS + SQUARES,
        "交互作用の項も追加" to COLUMNS + pairsWithReplacement(COLUMNS).map { (left, right) -> termName(left, right) },
    )
```

「交互作用の項も追加」の列名は、`pairsWithReplacement` と `termName` から組み立てています。列名の組み立て方を 1 か所にとどめるためです。`linkedMapOf` は、入れた順を保つ `Map` を作るので、表示の順番が定義の順になります。

```bash
./gradlew runChapter -Pchapter=09
```

```text
訓練データ: 70 件, テストデータ: 30 件
特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
決定係数:
  元の特徴量（3 列）: 訓練 0.6293, テスト 0.6457
  2 乗の項を追加（6 列）: 訓練 0.7963, テスト 0.7975
  交互作用の項も追加（9 列）: 訓練 0.8042, テスト 0.7995
訓練データの PRICE の外れ値: 8 件
  外れ値を除いて 2 乗の項を追加: 訓練 0.7055, テスト 0.7786
天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
```

結果を表にまとめます。

| 特徴量 | 列数 | 訓練データ | テストデータ |
|--------|------|-----------|------------|
| 元の特徴量 | 3 | 0.6293 | 0.6457 |
| 2 乗の項を追加 | 6 | 0.7963 | 0.7975 |
| 交互作用の項も追加 | 9 | 0.8042 | **0.7995** |
| 外れ値を除いて 2 乗の項を追加 | 6 | 0.7055 | 0.7786 |

この表から、次のことが読み取れます。

- **2 乗の項は効いた**: テストデータの決定係数が 0.6457 から 0.7975 に上がりました。価格と部屋数・低所得者の割合の関係が直線ではなく曲線であることを、2 乗の項が捉えています（9.10 節の散布図でも確認します）
- **交互作用の項はほとんど変わらなかった**: 3 列を足しても、テストデータの決定係数は 0.7975 から 0.7995 と、わずか 0.002 しか上がりませんでした。Python 版では、同じ 9 列でテストデータの決定係数が 0.7283 から 0.5799 に下がり、過学習がはっきり表れていました。違いは、訓練データとテストデータに入った行が違うことだけです。70 件の訓練データに 9 列という「多すぎるかもしれない」状態では、どの行で学習し、どの行で評価したかによって結論が変わりうる、ということです
- **外れ値を除いても良くならなかった**: IQR で検出した 8 件を除くと、テストデータの決定係数は 0.7975 から 0.7786 に下がりました。Python 版と同じ向きの結果です。価格の高い地域は「測定の誤り」ではなく「実際に高い」データなので、除くとモデルは高価格帯を学べなくなり、テストデータに含まれる高価格帯の予測を外すようになります

外れ値の **検出** は機械的にできますが、除くかどうかはデータの意味を見て決める必要があります。そして、特徴量を増やすかどうかは、必ず学習に使っていないテストデータの評価で判断します。交互作用の項のように分け方で結論が揺れる場合は、1 回の分割の結果だけで決めず、第 11 章の交差検証で複数の分け方の平均を見ます。

実データのテストでは、この結果を固定しています。

```kotlin
class FeatureEngineeringDataTest {
    private val bostonCsv = File(dataDir(), "Boston.csv")
    private val bikeTsv = File(dataDir(), "bike.tsv")
    private val weatherCsv = File(dataDir(), "weather.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(
            listOf(bostonCsv, bikeTsv, weatherCsv).all { it.exists() },
            "学習データ Boston.csv・bike.tsv・weather.csv が配置されていない（gulp data:setup）",
        )
    }

    private fun bostonSplit(): TrainTestSplit<Double> = prepareBoston(bostonCsv, testSize = 0.3, seed = 0)

    @Test
    fun `実データを70件と30件に分けて欠損値を補完する`() {
        val split = bostonSplit()

        assertEquals(70 to 30, split.xTrain.rowsCount() to split.xTest.rowsCount())
        assertEquals(0, countMissing(split.xTrain).values.sum())
        assertEquals(0, countMissing(split.xTest).values.sum())
    }

    @Test
    fun `2乗の項を加えるとテストデータの決定係数が上がる`() {
        val split = bostonSplit()

        val (_, base) = scoreFeatureSet(split, COLUMNS, COLUMNS)
        val (_, squares) = scoreFeatureSet(split, COLUMNS, COLUMNS + SQUARES)

        assertEquals(0.6457, base, absoluteTolerance = 1e-4)
        assertEquals(0.7975, squares, absoluteTolerance = 1e-4)
    }

    @Test
    fun `天気ごとの平均利用者数を求める`() {
        val joined = joinWeather(loadBike(bikeTsv), loadWeather(weatherCsv))

        val means = meanCountByWeather(joined)

        assertEquals(731, joined.rowsCount())
        assertEquals(listOf("晴れ", "曇り", "雨"), means.keys.toList())
        assertValues(listOf(4876.8, 4052.7, 1803.3), means.values.map { Math.round(it * 10) / 10.0 })
    }
}
```

`assumeTrue` の条件は、`all { it.exists() }` で 3 つのファイルがすべてあるかを確かめています。1 つでも無ければ、クラスの中のテストはすべてスキップされます。表示の `main` を確かめるテストもあります（完成コードを参照）。`prepareBoston` の型の問題を除き、実データのテストと `main` は、実データで出力を確かめながら書いたので Red を経ていません。

```bash
./gradlew test --tests "chapter09.*"
```

第 9 章のテストは 29 件すべて通ります。学習データが無い環境では、実データのテスト 4 件がスキップされ、25 件が通ります。

```text
FeatureEngineeringDataTest > 実行すると特徴量エンジニアリングの結果を表示する() SKIPPED
FeatureEngineeringDataTest > 2乗の項を加えるとテストデータの決定係数が上がる() SKIPPED
FeatureEngineeringDataTest > 天気ごとの平均利用者数を求める() SKIPPED
FeatureEngineeringDataTest > 実データを70件と30件に分けて欠損値を補完する() SKIPPED
BUILD SUCCESSFUL in 11s
```

## 9.10 Notebook による探索と可視化

テストで確かめた結果を、グラフでも確認します。Notebook は `apps/kotlin/notebooks/chapter09_boston_exploration.ipynb` にあります。学習データを再配布しないため、この記事にはグラフの画像を載せていません。手元で Notebook を実行して確認してください。

### 準備

```kotlin
%use dataframe(0.15.0), kandy(0.8.0)
```

```kotlin
@file:DependsOn("../build/libs/getting-started-ml.jar")
```

```kotlin
import chapter09.COLUMNS
import chapter09.Standardizer
import chapter09.iqrOutliers
import chapter09.joinWeather
import chapter09.loadBike
import chapter09.loadWeather
import chapter09.meanCountByWeather
import chapter09.prepareBoston
import chapter09.quantile
import java.io.File

// Notebook は notebooks/ で実行されるので、学習データの既定の場所を 1 つ上にずらす
val dataDirectory = dataset.dataDir { name -> System.getenv(name) ?: "../../data/sukkiri-ml" }
val split = prepareBoston(File(dataDirectory, "Boston.csv"), testSize = 0.3, seed = 0)
```

Notebook からもテスト済みの関数を使います。Notebook の中で前処理を書き直すと、テストしたコードと Notebook のコードが食い違っていくからです。

### 標準化の前後を比べる

```kotlin
val train = split.xTrain.select("RM", "LSTAT", "PTRATIO")
val standardized = Standardizer.fit(train).transform(train)
train.rowsCount() to train.columnsCount()
```

```text
(70, 3)
```

```kotlin
dataFrameOf(
    "標準化前" to train["RM"].values().map { (it as Number).toDouble() },
    "標準化後" to standardized["RM"].values().map { it as Double },
).plot {
    points {
        x("標準化前")
        y("標準化後")
    }
    layout.title = "RM の標準化の前後"
}
```

横軸に標準化前の値、縦軸に標準化後の値を取ると、点は右上がりの 1 本の直線に並びます。標準化は「平均を引いて標準偏差で割る」1 次式の変換なので、値の大小の順番も、値どうしの間隔の比も変えません。分布の形を変えずに、位置と幅だけをそろえる変換です。

Python 版では、標準化の前後の分布をヒストグラムで比べました。Kotlin 版でも Kandy の `histogram` を試しましたが、DataFrame 0.15.0 と Kandy 0.8.0 の組み合わせでは、実行時に `NoSuchMethodError`（DataFrame の集計の内部 API が見つからない）で失敗しました。そのため、集計を伴わない散布図で確かめています。

```kotlin
dataFrameOf(
    "列" to COLUMNS,
    "標準化前の平均" to COLUMNS.map { train[it].values().map { v -> (v as Number).toDouble() }.average() },
    "標準化後の標準偏差" to COLUMNS.map { standardized[it].cast<Double>().std(ddof = 0) },
)
```

| 列 | 標準化前の平均 | 標準化後の標準偏差 |
|----|--------------|-----------------|
| RM | 6.23 | 1.00 |
| LSTAT | 11.40 | 1.00 |
| PTRATIO | 18.58 | 1.00 |

表は、Notebook の出力を小数第 2 位に丸めたものです。

### 特徴量と価格の関係を見る

```kotlin
val priced = split.xTrain.add("PRICE") { split.tTrain[index()] }
priced.plot {
    points {
        x("RM")
        y("PRICE")
    }
    layout.title = "訓練データの RM と PRICE"
}
```

同じ形で LSTAT と PRICE の散布図も描きます。相関係数は、訓練データの全 14 列について求め、絶対値の大きい順に並べます。

```kotlin
fun pearson(xs: List<Double>, ys: List<Double>): Double {
    val mx = xs.average()
    val my = ys.average()
    val cov = xs.zip(ys).sumOf { (x, y) -> (x - mx) * (y - my) }
    return cov / Math.sqrt(xs.sumOf { (it - mx) * (it - mx) } * ys.sumOf { (it - my) * (it - my) })
}

val features = split.xTrain.columnNames()
dataFrameOf(
    "列" to features,
    "PRICE との相関係数" to features.map { name -> pearson(split.xTrain[name].values().map { (it as Number).toDouble() }, split.tTrain) },
).sortByDesc { "PRICE との相関係数"<Double>().map { Math.abs(it) } }
```

| 列 | PRICE との相関係数 |
|----|-----------------|
| LSTAT | −0.692 |
| RM | 0.654 |
| PTRATIO | −0.370 |
| INDUS | −0.302 |

表は上位 4 列を小数第 3 位に丸めたものです。散布図からは次のことが読み取れます。

- **RM**: 部屋数が多いほど価格が高く、部屋数が特に多い範囲では価格が急に上がります
- **LSTAT**: 低所得者の割合が高いほど価格が低く、割合が低い範囲で価格が急に上がる、下に凸の曲線になっています

RM と LSTAT の関係が直線ではなく曲線であることが、2 乗の項で決定係数が上がった理由です。相関係数は直線的な関係の強さしか表さないので、散布図で形を見ることが大切です。

### 外れ値を見る

```kotlin
val q1 = quantile(split.tTrain, 0.25)
val q3 = quantile(split.tTrain, 0.75)
val outliers = split.tTrain.filterIndexed { i, _ -> iqrOutliers(split.tTrain)[i] }
dataFrameOf(
    "外れ値の件数" to listOf(outliers.size),
    "高い側" to listOf(outliers.count { it > q3 }),
    "低い側" to listOf(outliers.count { it < q1 }),
)
```

| 外れ値の件数 | 高い側 | 低い側 |
|------------|--------|--------|
| 8 | 7 | 1 |

IQR で外れ値とみなされた 8 件のうち、7 件は価格の高い側、1 件は低い側にありました。Notebook では、価格を小さい順に並べ、外れ値かどうかで色を分けた散布図も描いています（Python 版の箱ひげ図の代わりです）。散布図と見比べると、価格の高い点は部屋数が多く低所得者の割合が低い地域で、特徴量と矛盾しない値です。高価格帯の地域が少ないだけで、誤ったデータとは言えません。これが、外れ値を除くとテストデータの決定係数が下がった理由です。

### 天気ごとの利用者数を見る

```kotlin
val joined = joinWeather(loadBike(File(dataDirectory, "bike.tsv")), loadWeather(File(dataDirectory, "weather.csv")))
val means = meanCountByWeather(joined)
dataFrameOf("天気" to means.keys.toList(), "平均利用者数" to means.values.toList()).plot {
    bars {
        x("天気")
        y("平均利用者数")
    }
    layout.title = "天気ごとの平均利用者数"
}
```

晴れ（4876.8 人）・曇り（4052.7 人）・雨（1803.3 人）の順に利用者が少なくなり、雨の日は晴れの日の半分以下です。天気 ID という数字のままでは分からなかったこの傾向が、表を結合したことで見えるようになりました。天気を利用者数の予測に使うなら、天気の名前をダミー変数にして特徴量に加えます。この平均値は Python 版と同じです。分割をしていない、全件の集計だからです。

### Notebook の片付け

Notebook の出力には学習データ由来のグラフが含まれるので、コミットする前に出力セルを消します（第 6 章）。

```bash
./gradlew notebookStrip
./gradlew notebookVerify
```

## 9.11 リファクタリング

TDD でテストを 1 つずつ足していく間、関数はすべて `FeatureEngineering.kt` の末尾に追記していきました。テストが揃ったところで `./gradlew check` を実行すると、detekt（第 5 章）が次の問題を指摘しました（パスは `apps/kotlin/` からの相対パスに直しています）。

```text
src\main\kotlin\chapter09\FeatureEngineering.kt:1:1: File '...\FeatureEngineering.kt' with '19' functions detected. Defined threshold inside files is set to '11' [TooManyFunctions]
src\main\kotlin\chapter09\FeatureEngineering.kt:70:49: In most cases using a spread operator causes a full copy of the array to be created before calling a method. This may result in a performance penalty. [SpreadOperator]
src\main\kotlin\chapter09\FeatureEngineering.kt:183:65: In most cases using a spread operator causes a full copy of the array to be created before calling a method. This may result in a performance penalty. [SpreadOperator]
src\main\kotlin\chapter09\FeatureEngineering.kt:184:63: In most cases using a spread operator causes a full copy of the array to be created before calling a method. This may result in a performance penalty. [SpreadOperator]
src\main\kotlin\chapter09\FeatureEngineering.kt:99:31: This expression contains a magic number. Consider defining it to a well named constant. [MagicNumber]
src\main\kotlin\chapter09\FeatureEngineering.kt:100:31: This expression contains a magic number. Consider defining it to a well named constant. [MagicNumber]
src\main\kotlin\chapter09\Main.kt:31:93: This expression contains a magic number. Consider defining it to a well named constant. [MagicNumber]
src\main\kotlin\chapter09\Main.kt:31:126: This expression contains a magic number. Consider defining it to a well named constant. [MagicNumber]
```

第 5 章と同じく、設定を変えるのではなく、指摘ごとにコードを直しました。どの変更の後も、29 件のテストが通ることを確かめています。

**TooManyFunctions（ファイルを分ける）**: 1 つのファイルに 19 個の関数があり、detekt の既定の上限 11 個を超えていました。Python 版では 1 つのモジュールの中で関数を並べ替えましたが、Kotlin 版では技法ごとにファイルを分けました。Kotlin では、同じパッケージのファイルどうしは import なしで互いの関数を呼べるので、ファイルを分けても呼び出し側は変わりません。

| ファイル | 中身 |
|---------|------|
| `Dummies.kt` | `dummyCategories`・`encodeDummies` |
| `Standardizer.kt` | `Standardizer` |
| `PolynomialFeatures.kt` | `polynomialFeatures`・`pairsWithReplacement`・`termName` |
| `Outliers.kt` | `quantile`・`iqrOutliers`・`removeTargetOutliers` |
| `BikeWeather.kt` | `loadBike`・`loadWeather`・`joinWeather`・`meanCountByWeather` |
| `LinearModel.kt` | `LinearModel`・`fitLinearRegression`・`rSquared` |
| `Boston.kt` | `prepareBoston`・`scoreFeatureSet` |

ファイルをまたいで使う補助の拡張関数（`doubles`・`toRows` など）は、`private` から `internal` に変えました。`private` なトップレベル関数は同じファイルからしか見えませんが、`internal` なら同じモジュールのどこからでも見え、モジュールの外には公開されません。

**SpreadOperator（スプレッド演算子を使わない）**: `select(*terms.toTypedArray())` は、リストを配列に変換し、さらにスプレッド演算子で配列をもう一度コピーします。Kotlin DataFrame の `select` には列名のリストを受け取る版が無いので、列を取り出して `dataFrameOf` で新しいデータフレームを作る拡張関数にしました。

```kotlin
internal fun AnyFrame.selectColumns(names: List<String>): AnyFrame = dataFrameOf(names.map { this[it] })
```

**MagicNumber（名前付きの定数にする）**: 四分位数の `0.25`・`0.75` を `FIRST_QUARTILE`・`THIRD_QUARTILE` に、表示の桁数の `4` を `SCORE_DIGITS` にしました。桁数は、`2`（平均）と `1`（利用者数）も同じ並びで `MEAN_DIGITS`・`COUNT_DIGITS` と名前を付け、`format` の呼び出しをそろえています。

最後に、テストのファイルの書き出しを `writeFile` にまとめ、`model.weights.map { it }` という何もしない変換を取り除きました。

<details>
<summary>この章の完成コード（src/main/kotlin/chapter09/）</summary>

```kotlin
// Dummies.kt
package chapter09

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.remove

fun dummyCategories(values: List<String?>): List<String> =
    values
        .filterNotNull()
        .distinct()
        .sorted()
        .drop(1)

fun encodeDummies(
    df: AnyFrame,
    column: String,
    categories: List<String>,
): AnyFrame =
    categories.fold(df.remove(column)) { encoded, category ->
        val flags = df[column].values().map { if (it == category) 1 else 0 }
        encoded.add("${column}_$category") { flags[index()] }
    }
```

```kotlin
// Standardizer.kt
package chapter09

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.convert
import org.jetbrains.kotlinx.dataframe.api.mean
import org.jetbrains.kotlinx.dataframe.api.std
import org.jetbrains.kotlinx.dataframe.api.with

data class Standardizer(
    val means: Map<String, Double>,
    val stds: Map<String, Double>,
) {
    fun transform(df: AnyFrame): AnyFrame =
        means.keys.fold(df) { standardized, column ->
            standardized.convert(column).with { ((it as Number).toDouble() - means.getValue(column)) / stds.getValue(column) }
        }

    companion object {
        fun fit(df: AnyFrame): Standardizer =
            Standardizer(
                means = df.columnNames().associateWith { df[it].cast<Number>().mean() },
                stds = df.columnNames().associateWith { df[it].cast<Number>().std(ddof = 0).takeIf { std -> std != 0.0 } ?: 1.0 },
            )
    }
}
```

```kotlin
// PolynomialFeatures.kt
package chapter09

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf

internal fun AnyFrame.doubles(column: String): List<Double> = this[column].values().map { (it as Number).toDouble() }

internal fun AnyFrame.selectColumns(names: List<String>): AnyFrame = dataFrameOf(names.map { this[it] })

fun polynomialFeatures(
    df: AnyFrame,
    columns: List<String>,
): AnyFrame =
    pairsWithReplacement(columns).fold(df.selectColumns(columns)) { features, (left, right) ->
        val leftValues = df.doubles(left)
        val rightValues = df.doubles(right)
        features.add(termName(left, right)) { leftValues[index()] * rightValues[index()] }
    }

fun <T> pairsWithReplacement(items: List<T>): List<Pair<T, T>> =
    items.indices.flatMap { i -> (i until items.size).map { j -> items[i] to items[j] } }

fun termName(
    left: String,
    right: String,
): String = if (left == right) "$left^2" else "$left $right"
```

```kotlin
// Outliers.kt
package chapter09

import chapter02.TrainTestSplit
import kotlin.math.ceil
import kotlin.math.floor

private const val FIRST_QUARTILE = 0.25
private const val THIRD_QUARTILE = 0.75

fun quantile(
    values: List<Double>,
    q: Double,
): Double {
    val sorted = values.sorted()
    val position = (sorted.size - 1) * q
    val lower = floor(position).toInt()
    val upper = ceil(position).toInt()
    return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
}

fun iqrOutliers(
    values: List<Double>,
    k: Double = 1.5,
): List<Boolean> {
    val q1 = quantile(values, FIRST_QUARTILE)
    val q3 = quantile(values, THIRD_QUARTILE)
    val iqr = q3 - q1
    return values.map { it < q1 - k * iqr || it > q3 + k * iqr }
}

fun removeTargetOutliers(split: TrainTestSplit<Double>): TrainTestSplit<Double> {
    val outliers = iqrOutliers(split.tTrain)
    val keep = outliers.indices.filterNot { outliers[it] }
    return split.copy(xTrain = split.xTrain[keep], tTrain = split.tTrain.slice(keep))
}
```

```kotlin
// BikeWeather.kt
package chapter09

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.innerJoin
import org.jetbrains.kotlinx.dataframe.api.mean
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.api.sortByDesc
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.File
import java.nio.charset.Charset

fun loadBike(tsvFile: File): AnyFrame = DataFrame.readCSV(tsvFile, delimiter = '\t')

fun loadWeather(csvFile: File): AnyFrame = DataFrame.readCSV(csvFile, charset = Charset.forName("Shift_JIS"))

fun joinWeather(
    bike: AnyFrame,
    weather: AnyFrame,
): AnyFrame = bike.innerJoin(weather, "weather_id")

fun meanCountByWeather(joined: AnyFrame): Map<String, Double> =
    joined
        .groupBy("weather")
        .mean("cnt")
        .sortByDesc("cnt")
        .rows()
        .associate { it["weather"] as String to (it["cnt"] as Number).toDouble() }
```

```kotlin
// LinearModel.kt
package chapter09

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.tribuo.math.la.DenseMatrix
import org.tribuo.math.la.DenseVector

internal fun AnyFrame.toRows(): Array<DoubleArray> =
    Array(rowsCount()) { row -> DoubleArray(columnsCount()) { column -> (columns()[column][row] as Number).toDouble() } }

data class LinearModel(
    val intercept: Double,
    val weights: List<Double>,
) {
    fun predict(rows: Array<DoubleArray>): List<Double> = rows.map { row -> intercept + row.indices.sumOf { weights[it] * row[it] } }
}

fun fitLinearRegression(
    rows: Array<DoubleArray>,
    t: List<Double>,
): LinearModel {
    val design = DenseMatrix.createDenseMatrix(Array(rows.size) { doubleArrayOf(1.0) + rows[it] })
    val transposed = design.transpose()
    val cholesky =
        transposed.matrixMultiply(design).choleskyFactorization().orElseThrow {
            IllegalArgumentException("特徴量の列が互いに独立でないため、正規方程式を解けません")
        }
    val beta = cholesky.solve(transposed.leftMultiply(DenseVector.createDenseVector(t.toDoubleArray()))).toArray()
    return LinearModel(intercept = beta.first(), weights = beta.drop(1))
}

fun rSquared(
    actual: List<Double>,
    predicted: List<Double>,
): Double {
    val mean = actual.average()
    val residual = actual.zip(predicted).sumOf { (a, p) -> (a - p) * (a - p) }
    val total = actual.sumOf { (it - mean) * (it - mean) }
    return 1 - residual / total
}
```

```kotlin
// Boston.kt
package chapter09

import chapter02.TrainTestSplit
import chapter02.columnMeans
import chapter02.fillMissing
import chapter02.splitTrainTest
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.convert
import org.jetbrains.kotlinx.dataframe.api.remove
import org.jetbrains.kotlinx.dataframe.api.with
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.File

const val TARGET = "PRICE"

// 欠損値のある整数の列（Int?）には平均値（Double）を入れられないので、特徴量をすべて Double? にそろえる
private fun AnyFrame.withDoubleColumns(): AnyFrame =
    columnNames().fold(this) { df, name -> df.convert(name).with { (it as Number?)?.toDouble() } }

fun prepareBoston(
    csvFile: File,
    testSize: Double,
    seed: Int,
): TrainTestSplit<Double> {
    val df = DataFrame.readCSV(csvFile)
    val encoded = encodeDummies(df, "CRIME", dummyCategories(df["CRIME"].values().map { it as String? }))
    val x = encoded.remove(TARGET).withDoubleColumns()
    val split = splitTrainTest(x, encoded.doubles(TARGET), testSize, seed)
    val means = columnMeans(split.xTrain, x.columnNames())
    return split.copy(
        xTrain = fillMissing(split.xTrain, means),
        xTest = fillMissing(split.xTest, means),
    )
}

fun scoreFeatureSet(
    split: TrainTestSplit<Double>,
    columns: List<String>,
    terms: List<String>,
): Pair<Double, Double> {
    val train = polynomialFeatures(split.xTrain, columns).selectColumns(terms)
    val test = polynomialFeatures(split.xTest, columns).selectColumns(terms)
    val standardizer = Standardizer.fit(train)
    val xTrain = standardizer.transform(train).toRows()
    val xTest = standardizer.transform(test).toRows()
    val model = fitLinearRegression(xTrain, split.tTrain)
    return rSquared(split.tTrain, model.predict(xTrain)) to rSquared(split.tTest, model.predict(xTest))
}
```

```kotlin
// Main.kt
package chapter09

import dataset.dataDir
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.mean
import org.jetbrains.kotlinx.dataframe.api.std
import java.io.File
import java.util.Locale
import kotlin.math.abs

private const val TEST_SIZE = 0.3
private const val SEED = 0

// 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす
private const val ZERO_TOLERANCE = 1e-9
private const val SCORE_DIGITS = 4
private const val MEAN_DIGITS = 2
private const val COUNT_DIGITS = 1

val COLUMNS = listOf("RM", "LSTAT", "PTRATIO")
val SQUARES = listOf("RM^2", "LSTAT^2", "PTRATIO^2")
val FEATURE_SETS =
    linkedMapOf(
        "元の特徴量" to COLUMNS,
        "2 乗の項を追加" to COLUMNS + SQUARES,
        "交互作用の項も追加" to COLUMNS + pairsWithReplacement(COLUMNS).map { (left, right) -> termName(left, right) },
    )

private fun format(
    value: Double,
    digits: Int,
): String = "%.${digits}f".format(Locale.ROOT, if (abs(value) < ZERO_TOLERANCE) 0.0 else value)

private fun formatScores(scores: Pair<Double, Double>): String =
    "訓練 ${format(scores.first, SCORE_DIGITS)}, テスト ${format(scores.second, SCORE_DIGITS)}"

fun main() {
    val split = prepareBoston(File(dataDir(), "Boston.csv"), testSize = TEST_SIZE, seed = SEED)
    println("訓練データ: ${split.xTrain.rowsCount()} 件, テストデータ: ${split.xTest.rowsCount()} 件")
    println("特徴量の列: ${split.xTrain.columnNames().joinToString(", ")}")

    val rm = Standardizer.fit(split.xTrain).transform(split.xTrain)["RM"].cast<Double>()
    println("標準化した訓練データの RM: 平均 ${format(rm.mean(), MEAN_DIGITS)}, 標準偏差 ${format(rm.std(ddof = 0), MEAN_DIGITS)}")

    println("決定係数:")
    for ((name, terms) in FEATURE_SETS) {
        println("  $name（${terms.size} 列）: ${formatScores(scoreFeatureSet(split, COLUMNS, terms))}")
    }

    println("訓練データの PRICE の外れ値: ${iqrOutliers(split.tTrain).count { it }} 件")
    val removed = scoreFeatureSet(removeTargetOutliers(split), COLUMNS, COLUMNS + SQUARES)
    println("  外れ値を除いて 2 乗の項を追加: ${formatScores(removed)}")

    val joined = joinWeather(loadBike(File(dataDir(), "bike.tsv")), loadWeather(File(dataDir(), "weather.csv")))
    val means = meanCountByWeather(joined)
    println("天気ごとの平均利用者数: " + means.entries.joinToString(", ") { (weather, count) -> "$weather=${format(count, COUNT_DIGITS)}" })
}
```

</details>

## 9.12 まとめ

この章では、特徴量エンジニアリングの 5 つの技法を TDD で自作し、標準化を Tribuo と突き合わせました。

| 技法 | 自作した関数・クラス | 突き合わせたライブラリ | 落とし穴 |
|------|------------------|-------------------|---------|
| ダミー変数 | `dummyCategories`・`encodeDummies` | — | 訓練データとテストデータで列をそろえる |
| 標準化 | `Standardizer` | Tribuo の `MeanStdDevTransformation` | 標準偏差の定義（`ddof`）がライブラリで違う、分散 0 の列で NaN、テストデータの平均を使わない |
| 多項式特徴量 | `polynomialFeatures`・`pairsWithReplacement` | — | 列数が急に増えて過学習しやすい |
| 外れ値の検出 | `quantile`・`iqrOutliers` | — | 検出はできても、除くかどうかはデータの意味で決める |
| 表の結合 | `loadBike`・`loadWeather`・`joinWeather` | — | 区切り文字と文字コード（間違えても例外にならず文字化けする）、内部結合で消える行 |

実データでは、2 乗の項を加えるとテストデータの決定係数が 0.6457 から 0.7975 に上がりました。交互作用の項を加えてもほとんど変わらず（0.7995）、外れ値を除くと 0.7786 に下がりました。Python 版では交互作用の項ではっきり下がっていたので、分け方によって結論が揺れる判断もあることが分かります。特徴量を作るのは手段で、その効果は学習に使っていないデータで測って判断します。

Kotlin 版ならではの学びもありました。

1. **列の型** — 欠損のある整数の列は `Int?` になり、平均値の `Double` を入れられない。実データで動かして初めて見つかった
2. **ライブラリの既定値** — Kotlin DataFrame の `std`（`ddof = 1`）と Tribuo は不偏標準偏差を使う。同じ「標準偏差」でも定義を確かめる
3. **静的解析によるリファクタリング** — detekt の指摘を受けて、技法ごとのファイルに分け、`internal` で可視性を整えた

次の章では、分類のモデルを増やし、ロジスティック回帰と、第 3 章の決定木を組み合わせたランダムフォレストを実装します。
