---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "Kotlin DataFrame で iris データの欠損値を訓練データの平均値で補完し、Random(seed) による訓練・テストデータ分割を TDD で実装する。"
tags: [article,getting-start-ml,kotlin]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-17T03:55:25Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

第 1 章では、欠損のないきれいなデータを読み込み、人間が書いたルールで判定しました。現実のデータには、空欄（欠損値）が混ざっています。

この章では、アヤメ（iris）のデータを題材に、欠損値を補完し、データを **訓練データ** と **テストデータ** に分けるところまでを TDD で実装します。データの扱いには [Kotlin DataFrame](https://kotlin.github.io/dataframe/) を使います。

[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md) と同じ TODO リストで進めます。Kotlin 版では、欠損値が null 許容型としてどう現れるか、DataFrame が不変であること、シード付きの乱数の扱いに注目してください。

## 2.2 題材とデータ

### iris.csv

`iris.csv` には、アヤメの花 150 件の測定値と品種が記録されています。

| 列 | 意味 | Kotlin DataFrame での型 |
|----|------|-----------------------|
| がく片長さ | がく片の長さ | `Double?` |
| がく片幅 | がく片の幅 | `Double?` |
| 花弁長さ | 花弁の長さ | `Double?` |
| 花弁幅 | 花弁の幅 | `Double?` |
| 種類 | 品種（3 種類が 50 件ずつ） | `String` |

特徴量の 4 列には合わせて 7 件の欠損値があります。欠損値を含む列は、Kotlin DataFrame が自動で `Double?`（null を取りうる `Double`）として読み込みます。

### 訓練データとテストデータ

モデルの良し悪しは、学習に使っていないデータでどれだけ当たるかで測ります。そこで、150 件を 7 対 3 の 105 件（訓練データ）と 45 件（テストデータ）に分けます。欠損値を補完する平均値は **訓練データだけ** から求め、その値で両方を補完します。データ全体の平均値を使うと、テストデータの情報が学習側に漏れるためです（データリーク）。

### Python 版と数値が変わる理由

分け方の手順（シード付きでシャッフルし、テストデータの件数を切り上げる）は Python 版と同じですが、乱数生成器が NumPy と Kotlin で異なるため、どの行がテストデータに入るかは Python 版と一致しません。件数は同じ 105 件と 45 件ですが、補完に使う平均値や、第 3 章以降の正解率などの数値は Python 版と変わります。

## 2.3 開発環境の準備

Kotlin DataFrame 0.15.0 を依存に追加します。`gradle/libs.versions.toml` に版を書き、`build.gradle.kts` から参照します。

```toml
[versions]
kotlin = "2.4.20"
ktlint = "1.8.0"
dataframe = "0.15.0"
tribuo = "4.3.2"
slf4j = "2.0.16"

[libraries]
dataframe = { module = "org.jetbrains.kotlinx:dataframe", version.ref = "dataframe" }
tribuo-classification-tree = { module = "org.tribuo:tribuo-classification-tree", version.ref = "tribuo" }
slf4j-nop = { module = "org.slf4j:slf4j-nop", version.ref = "slf4j" }
ktlint-cli = { module = "com.pinterest.ktlint:ktlint-cli", version.ref = "ktlint" }
```

```kotlin
dependencies {
    implementation(libs.dataframe)
    implementation(libs.tribuo.classification.tree)
    // DataFrame・Tribuo が使う SLF4J の警告を出さないための、何もしないログ実装
    runtimeOnly(libs.slf4j.nop)
    testImplementation(kotlin("test"))
}
```

Tribuo は第 3 章で使います。slf4j-nop は、DataFrame が内部で使うログ出力の仕組み（SLF4J）の実装が無いという警告を消すためのものです。

ライブラリの選定理由は [ADR 002](../../../adr/002-kotlin-ml-libraries.md) を参照してください。Kotlin DataFrame の 1.0 系は API が変わっており、読み込み関数は 0.15.0 では `readCSV`、1.0 系では `readCsv` という名前です。

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] iris.csv を読み込む
  - [ ] BOM 付き CSV の列名に BOM が残らない
  - [ ] 空欄を欠損値（null）として読み込む
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

## 2.5 Kotlin DataFrame で読み込む

### 学習用テストで DataFrame の振る舞いを確かめる

第 1 章では、BOM を自分で取り除きました。Kotlin DataFrame の読み込みはどうでしょうか。ライブラリの振る舞いを確かめるテスト（学習用テスト）を書きます。

> 学習用テスト
>
> 外部のソフトウェアのテストを書くべきだろうか——そのソフトウェアに対して新しいことを初めて行おうとした段階で書いてみよう。
>
> — テスト駆動開発

```kotlin
// src/test/kotlin/chapter02/IrisPreprocessingTest.kt
private const val HEADER = "\uFEFFがく片長さ,がく片幅,花弁長さ,花弁幅,種類\n"

class LoadIrisTest {
    private val directory: Path = createTempDirectory()

    private fun writeCsv(rows: String): File = File(directory.toFile(), "iris.csv").apply { writeText(HEADER + rows) }

    @Test
    fun `BOM付きCSVを読み込むと列名にBOMが残らない`() {
        val df = loadIris(writeCsv("0.1,0.2,0.3,0.4,Iris-setosa\n"))

        assertEquals(listOf("がく片長さ", "がく片幅", "花弁長さ", "花弁幅", "種類"), df.columnNames())
    }

    @Test
    fun `空欄は欠損値のnullとして読み込む`() {
        val df = loadIris(writeCsv("0.1,,0.3,0.4,Iris-setosa\n"))

        assertNull(df["がく片幅"][0])
    }
}

class CountMissingTest {
    @Test
    fun `列ごとの欠損値の数を数える`() {
        val df =
            dataFrameOf(
                "がく片長さ" to listOf(0.1, null, null),
                "がく片幅" to listOf(0.2, 0.3, null),
                "種類" to listOf("Iris-setosa", "Iris-setosa", "Iris-virginica"),
            )

        assertEquals(mapOf("がく片長さ" to 2, "がく片幅" to 1, "種類" to 0), countMissing(df))
    }
}
```

`dataFrameOf("列名" to 値のリスト, ...)` で、列ごとに値を並べてデータフレームを作れます。Python 版の `pd.DataFrame({...})` に相当します。

```text
e: .../src/test/kotlin/chapter02/IrisPreprocessingTest.kt:20:18 Unresolved reference 'loadIris'.
e: .../src/test/kotlin/chapter02/IrisPreprocessingTest.kt:27:18 Unresolved reference 'loadIris'.
e: .../src/test/kotlin/chapter02/IrisPreprocessingTest.kt:29:30 No 'get' operator method providing array access.
e: .../src/test/kotlin/chapter02/IrisPreprocessingTest.kt:43:67 Unresolved reference 'countMissing'.
```

3 行目のエラーは、`loadIris` の戻り値の型が分からないため、`df["がく片幅"][0]` の添字アクセスも解決できないというものです。型が分からないと連鎖的にエラーになるのは、静的型付けの言語ならではです。

### Green: 明白な実装

```kotlin
// src/main/kotlin/chapter02/IrisPreprocessing.kt
fun loadIris(csvFile: File): AnyFrame = DataFrame.readCSV(csvFile)

fun countMissing(df: AnyFrame): Map<String, Int> = df.columns().associate { column -> column.name() to column.values().count { it == null } }
```

```text
CountMissingTest > 列ごとの欠損値の数を数える() PASSED
LoadIrisTest > 空欄は欠損値のnullとして読み込む() PASSED
LoadIrisTest > BOM付きCSVを読み込むと列名にBOMが残らない() PASSED
BUILD SUCCESSFUL in 3s
```

`readCSV` は BOM を取り除き、空欄を null として読み込むことが分かりました。`AnyFrame` は「列の型を静的には決めていないデータフレーム」（`DataFrame<*>`）の別名です。

| 観点 | 第 1 章の自作の読み込み | Kotlin DataFrame |
|------|----------------------|------------------|
| BOM | `removePrefix` で自分で取り除く | 取り除く |
| 値の型 | `toInt()` で自分で変換する | 列ごとに推定する（欠損があれば `Double?`） |
| 空欄 | 空文字列 | `null` |

## 2.6 平均値で欠損値を補完する

### 平均値を求める

```kotlin
class ColumnMeansTest {
    @Test
    fun `欠損値を除いて列ごとの平均値を求める`() {
        val df =
            dataFrameOf(
                "がく片長さ" to listOf(0.1, null, 0.3),
                "がく片幅" to listOf(0.2, 0.4, 0.9),
            )

        val means = columnMeans(df, listOf("がく片長さ", "がく片幅"))

        assertEquals(0.2, means.getValue("がく片長さ"), absoluteTolerance = 1e-12)
        assertEquals(0.5, means.getValue("がく片幅"), absoluteTolerance = 1e-12)
    }
}
```

### 補完する

補完に使う値は引数で受け取ります。元のデータを変えないことも、テストで約束します。

```kotlin
class FillMissingTest {
    @Test
    fun `欠損値を列ごとに指定した値で補完する`() {
        val df =
            dataFrameOf(
                "がく片長さ" to listOf(0.1, null),
                "がく片幅" to listOf(null, 0.4),
            )

        val filled = fillMissing(df, mapOf("がく片長さ" to 0.2, "がく片幅" to 0.5))

        assertEquals(listOf(0.1, 0.2), filled["がく片長さ"].toList())
        assertEquals(listOf(0.5, 0.4), filled["がく片幅"].toList())
    }

    @Test
    fun `元のデータフレームは変更しない`() {
        val df = dataFrameOf("がく片長さ" to listOf(0.1, null))

        fillMissing(df, mapOf("がく片長さ" to 0.2))

        assertEquals(1, countMissing(df).getValue("がく片長さ"))
    }
}
```

```text
e: .../src/test/kotlin/chapter02/IrisPreprocessingTest.kt:57:21 Unresolved reference 'columnMeans'.
e: .../src/test/kotlin/chapter02/IrisPreprocessingTest.kt:73:22 Unresolved reference 'fillMissing'.
```

```kotlin
fun columnMeans(
    df: AnyFrame,
    columns: List<String>,
): Map<String, Double> =
    columns.associateWith { name ->
        df[name]
            .values()
            .filterIsInstance<Number>()
            .map { it.toDouble() }
            .average()
    }

fun fillMissing(
    df: AnyFrame,
    values: Map<String, Double>,
): AnyFrame = values.entries.fold(df) { filled, (name, value) -> filled.fillNulls(name).with { value } }
```

- `filterIsInstance<Number>()` は、`Number` 型の値だけを残します。null は `Number` ではないので、ここで取り除かれます
- `fillNulls(name).with { value }` は、指定した列の null を値で埋めた **新しい** データフレームを返します。Kotlin DataFrame の操作はすべて元のデータフレームを変更しません
- `fold` は、初期値（元の `df`）から始めて、補完する列ごとに `fillNulls` を重ねていきます

```text
ColumnMeansTest > 欠損値を除いて列ごとの平均値を求める() PASSED
FillMissingTest > 元のデータフレームは変更しない() PASSED
FillMissingTest > 欠損値を列ごとに指定した値で補完する() PASSED
BUILD SUCCESSFUL in 3s
```

DataFrame 自身にも平均値を求める `mean` があります。自作の `columnMeans` と同じく欠損値を除いて計算することを、学習用テストで確かめておきます。

```kotlin
class DataFrameMeanLearningTest {
    @Test
    fun `DataFrameのmeanも欠損値を除いて平均値を求める`() {
        val df = dataFrameOf("がく片長さ" to listOf(0.1, null, 0.3))

        assertEquals(columnMeans(df, listOf("がく片長さ")).getValue("がく片長さ"), df["がく片長さ"].cast<Double?>().mean(), absoluteTolerance = 1e-12)
    }
}
```

`df["がく片長さ"]` の型は、要素の型が決まっていない列（`DataColumn<*>`）です。`cast<Double?>()` で `Double?` の列として扱うと宣言してから `mean` を呼びます。

## 2.7 特徴量と正解ラベルに分ける

正解ラベルは、第 3 章の決定木で扱いやすいように `List<String>` にします。

```kotlin
class SplitFeaturesAndTargetTest {
    @Test
    fun `特徴量の列と正解ラベルの列に分ける`() {
        val df =
            dataFrameOf(
                "がく片長さ" to listOf(0.1, 0.5),
                "花弁幅" to listOf(0.4, 0.8),
                "種類" to listOf("Iris-setosa", "Iris-virginica"),
            )

        val (x, t) = splitFeaturesAndTarget(df, "種類")

        assertEquals(listOf("がく片長さ", "花弁幅"), x.columnNames())
        assertEquals(listOf("Iris-setosa", "Iris-virginica"), t)
    }
}
```

```kotlin
fun splitFeaturesAndTarget(
    df: AnyFrame,
    target: String,
): Pair<AnyFrame, List<String>> = df.remove(target) to df[target].values().map { it.toString() }
```

## 2.8 訓練データとテストデータに分ける

### 分割結果を表す型と仮実装

```kotlin
data class TrainTestSplit(
    val xTrain: AnyFrame,
    val xTest: AnyFrame,
    val tTrain: List<String>,
    val tTest: List<String>,
)
```

0 から始まる番号を振ったデータで、件数だけを確かめます。

```kotlin
private fun numberedDataset(size: Int): Pair<AnyFrame, List<String>> {
    val x = dataFrameOf("x" to (0 until size).toList())
    val t = (0 until size).map { "label$it" }
    return x to t
}

class SplitTrainTestTest {
    @Test
    fun `テストデータの割合どおりの件数に分ける`() {
        val (x, t) = numberedDataset(10)

        val split = splitTrainTest(x, t, testSize = 0.3, seed = 0)

        assertEquals(7 to 3, split.xTrain.rowsCount() to split.xTest.rowsCount())
        assertEquals(7 to 3, split.tTrain.size to split.tTest.size)
    }
}
```

```kotlin
fun splitTrainTest(
    x: AnyFrame,
    t: List<String>,
    testSize: Double,
    seed: Int,
): TrainTestSplit = TrainTestSplit(xTrain = x.take(7), xTest = x.drop(7), tTrain = t.take(7), tTest = t.drop(7))
```

`take` と `drop` は、データフレームにもリストにも同じ名前で用意されています。

### 三角測量: 件数を一般化する

```kotlin
    @Test
    fun `件数が変わってもテストデータの割合どおりに分ける`() {
        val (x, t) = numberedDataset(20)

        val split = splitTrainTest(x, t, testSize = 0.25, seed = 0)

        assertEquals(15 to 5, split.xTrain.rowsCount() to split.xTest.rowsCount())
        assertEquals(15 to 5, split.tTrain.size to split.tTest.size)
    }
```

```text
SplitTrainTestTest > 件数が変わってもテストデータの割合どおりに分ける() FAILED
    org.opentest4j.AssertionFailedError: expected: <(15, 5)> but was: <(7, 13)>
SplitTrainTestTest > テストデータの割合どおりの件数に分ける() PASSED
23 tests completed, 1 failed
```

```kotlin
): TrainTestSplit {
    val nTrain = x.rowsCount() - ceil(x.rowsCount() * testSize).toInt()
    return TrainTestSplit(
        xTrain = x.take(nTrain),
        xTest = x.drop(nTrain),
        tTrain = t.take(nTrain),
        tTest = t.drop(nTrain),
    )
}
```

### 三角測量: 並び順に頼らない分け方にする

Kotlin DataFrame には pandas のような行インデックスが無いので、「特徴量と正解ラベルの対応を保つ」は値で確かめます。番号 `i` の行の正解ラベルは `"label$i"` なので、分けた後もこの対応が保たれているかを比べます。

```kotlin
    @Test
    fun `すべての行を重複なく訓練データとテストデータのどちらかに入れる`() {
        val (x, t) = numberedDataset(10)

        val split = splitTrainTest(x, t, testSize = 0.3, seed = 0)

        val trainRows = split.xTrain["x"].values().toSet()
        val testRows = split.xTest["x"].values().toSet()
        assertEquals((0 until 10).toSet(), trainRows + testRows)
        assertEquals(emptySet(), trainRows intersect testRows)
    }

    @Test
    fun `特徴量と正解ラベルの対応を保ったまま分ける`() {
        val (x, t) = numberedDataset(10)

        val split = splitTrainTest(x, t, testSize = 0.3, seed = 0)

        assertEquals(split.xTrain["x"].values().map { "label$it" }, split.tTrain)
        assertEquals(split.xTest["x"].values().map { "label$it" }, split.tTest)
    }

    @Test
    fun `同じシードなら同じ分け方になる`() {
        val (x, t) = numberedDataset(10)

        val first = splitTrainTest(x, t, testSize = 0.3, seed = 42)
        val second = splitTrainTest(x, t, testSize = 0.3, seed = 42)

        assertEquals(first.tTest, second.tTest)
    }

    @Test
    fun `シードが違えば違う分け方になる`() {
        val (x, t) = numberedDataset(10)

        val first = splitTrainTest(x, t, testSize = 0.3, seed = 0)
        val second = splitTrainTest(x, t, testSize = 0.3, seed = 1)

        assertNotEquals(first.tTest, second.tTest)
    }
```

`trainRows + testRows` は集合の和、`trainRows intersect testRows` は集合の積です。`intersect` は中置関数なので、演算子のように書けます。

```text
SplitTrainTestTest > シードが違えば違う分け方になる() FAILED
    org.opentest4j.AssertionFailedError: expected: not equal but was: <[label7, label8, label9]>
SplitTrainTestTest > 件数が変わってもテストデータの割合どおりに分ける() PASSED
SplitTrainTestTest > テストデータの割合どおりの件数に分ける() PASSED
SplitTrainTestTest > 特徴量と正解ラベルの対応を保ったまま分ける() PASSED
SplitTrainTestTest > 同じシードなら同じ分け方になる() PASSED
SplitTrainTestTest > すべての行を重複なく訓練データとテストデータのどちらかに入れる() PASSED
27 tests completed, 1 failed
```

Python 版と同じく、先頭から順に分けるだけでは最後のテストだけが失敗します。

### Green: シード付きの乱数で並べ替える

```kotlin
): TrainTestSplit {
    val positions = (0 until x.rowsCount()).shuffled(Random(seed))
    val nTrain = x.rowsCount() - ceil(x.rowsCount() * testSize).toInt()
    val train = positions.take(nTrain)
    val test = positions.drop(nTrain)
    return TrainTestSplit(
        xTrain = x[train],
        xTest = x[test],
        tTrain = t.slice(train),
        tTest = t.slice(test),
    )
}
```

- `kotlin.random.Random(seed)` は、シードが同じなら同じ乱数列を返す生成器です。`shuffled` にこれを渡すと、再現性のあるシャッフルになります
- `x[train]` は、行の位置のリストで行を取り出します。`t.slice(train)` はリストで同じことをします

```text
BUILD SUCCESSFUL in 3s
```

## 2.9 前処理をまとめる

```kotlin
class PrepareIrisTest {
    private val directory: Path = createTempDirectory()

    @Test
    fun `訓練データとテストデータのどちらにも欠損値が残らない`() {
        val csvFile =
            File(directory.toFile(), "iris.csv").apply {
                writeText(
                    HEADER +
                        "0.1,,0.3,0.4,Iris-setosa\n" +
                        "0.2,0.3,,0.5,Iris-setosa\n" +
                        ",0.4,0.5,0.6,Iris-virginica\n" +
                        "0.4,0.5,0.6,,Iris-virginica\n",
                )
            }

        val split = prepareIris(csvFile, testSize = 0.5, seed = 0)

        assertEquals(0, countMissing(split.xTrain).values.sum())
        assertEquals(0, countMissing(split.xTest).values.sum())
    }
}
```

```kotlin
const val TARGET = "種類"

fun prepareIris(
    csvFile: File,
    testSize: Double,
    seed: Int,
): TrainTestSplit {
    val (x, t) = splitFeaturesAndTarget(loadIris(csvFile), TARGET)
    val split = splitTrainTest(x, t, testSize, seed)
    val means = columnMeans(split.xTrain, x.columnNames())
    return split.copy(
        xTrain = fillMissing(split.xTrain, means),
        xTest = fillMissing(split.xTest, means),
    )
}
```

data class の `copy` は、指定したプロパティだけを差し替えた新しいインスタンスを作ります。Python 版の `dataclasses.replace` に相当します。

`prepareIris` と実データのテスト・`main` は、関数を組み合わせるだけなので、実装を書いてから実データで出力を確かめ、テストで固定しました。これらは Red を経ていません。

## 2.10 実データで前処理の結果を表示する

### テスト用ヘルパーを共通化する

第 1 章のテストファイルに置いていた標準出力を取り出す `captureStdout` は、この章の表示のテストでも使います。`src/test/kotlin/support/CaptureStdout.kt` に移し、どの章のテストからも `import support.captureStdout` で使えるようにしました。

### 実データのテスト

```kotlin
class IrisDataTest {
    private val csvFile = File(dataDir(), "iris.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ iris.csv が配置されていない（gulp data:setup）")
    }

    @Test
    fun `実データの列ごとの欠損値の数を数える`() {
        assertEquals(
            mapOf("がく片長さ" to 2, "がく片幅" to 1, "花弁長さ" to 2, "花弁幅" to 2, "種類" to 0),
            countMissing(loadIris(csvFile)),
        )
    }

    @Test
    fun `実データを105件と45件に分けて欠損値を補完する`() {
        val split = prepareIris(csvFile, testSize = 0.3, seed = 0)

        assertEquals(105 to 45, split.xTrain.rowsCount() to split.xTest.rowsCount())
        assertEquals(0, countMissing(split.xTrain).values.sum())
        assertEquals(0, countMissing(split.xTest).values.sum())
    }

    @Test
    fun `実行すると前処理の結果を表示する`() {
        val output = captureStdout { main() }

        assertEquals(
            "データ件数: 150\n" +
                "欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0\n" +
                "訓練データ: 105 件, テストデータ: 45 件\n" +
                "補完後の欠損値の数: 訓練データ 0, テストデータ 0\n",
            output,
        )
    }
}
```

```kotlin
// src/main/kotlin/chapter02/Main.kt
package chapter02

import dataset.dataDir
import java.io.File

private const val TEST_SIZE = 0.3
private const val SEED = 0

private fun formatCounts(counts: Map<String, Int>): String = counts.entries.joinToString(", ") { (column, count) -> "$column=$count" }

fun main() {
    val csvFile = File(dataDir(), "iris.csv")
    val df = loadIris(csvFile)
    val split = prepareIris(csvFile, testSize = TEST_SIZE, seed = SEED)
    println("データ件数: ${df.rowsCount()}")
    println("欠損値の数: ${formatCounts(countMissing(df))}")
    println("訓練データ: ${split.xTrain.rowsCount()} 件, テストデータ: ${split.xTest.rowsCount()} 件")
    val missingTrain = countMissing(split.xTrain).values.sum()
    val missingTest = countMissing(split.xTest).values.sum()
    println("補完後の欠損値の数: 訓練データ $missingTrain, テストデータ $missingTest")
}
```

```bash
./gradlew runChapter -Pchapter=02
```

```text
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
補完後の欠損値の数: 訓練データ 0, テストデータ 0
```

```bash
./gradlew test --tests "chapter02.*"
```

第 2 章のテストは 18 件すべて通ります。データが無い環境では、実データのテスト 3 件がスキップされます。

```text
IrisDataTest > 実行すると前処理の結果を表示する() SKIPPED
IrisDataTest > 実データを105件と45件に分けて欠損値を補完する() SKIPPED
IrisDataTest > 実データの列ごとの欠損値の数を数える() SKIPPED
BUILD SUCCESSFUL in 2s
```

## 2.11 リファクタリング

### 拡張関数で意図を名前にする

`countMissing` は、ktlint で整形すると 1 行に収まらず、列の欠損数を数える部分が読みにくく折り返されていました。「列の null の数を数える」処理を、列の型 `AnyCol` の **拡張関数** として切り出します。

```kotlin
private fun AnyCol.countNulls(): Int = values().count { it == null }

fun countMissing(df: AnyFrame): Map<String, Int> = df.columns().associate { it.name() to it.countNulls() }
```

拡張関数を使うと、ライブラリのクラスを変更せずに、そのクラスのメソッドのように呼べる関数を足せます。`private` にしているので、このファイルの外からは見えません。

### テストのデータを列ごとに作る

最初はテストのデータを `dataFrameOf("列1", "列2")(値1, 値2, ...)` と行の順に書いていましたが、ktlint で整形すると値が 1 つずつ縦に並び、どの値がどの列かが読み取れなくなりました。本章のテストのデータは、すべて `dataFrameOf("列名" to listOf(...))` の形で列ごとに書き直しています。

```bash
./gradlew check
```

```text
BUILD SUCCESSFUL in 5s
```

## 2.12 Notebook で探索する

### Kotlin Notebook を用意する

Notebook は `apps/kotlin/notebooks/chapter02_iris_exploration.ipynb` にあります。IntelliJ IDEA で開くと、Kotlin Notebook として実行できます。事前に `./gradlew jar` でプロジェクトの JAR を作っておきます。

```kotlin
%use dataframe(0.15.0), kandy(0.8.0)
@file:DependsOn("../build/libs/getting-started-ml.jar")
```

- `%use` は、Kotlin Notebook にライブラリを読み込む命令です。プロジェクトと同じ DataFrame 0.15.0 と、それに対応する可視化ライブラリ Kandy 0.8.0 を指定しています
- `@file:DependsOn` で、テスト済みのプロジェクトのコード（JAR）を読み込みます。Python 版と同じく、Notebook ではテスト済みの関数を呼ぶだけにします

```kotlin
import chapter02.countMissing
import chapter02.loadIris
import chapter02.prepareIris
import java.io.File

// Notebook は notebooks/ で実行されるので、学習データの既定の場所を 1 つ上にずらす
val irisCsv = File(dataset.dataDir { name -> System.getenv(name) ?: "../../data/sukkiri-ml" }, "iris.csv")
```

第 1 章で `dataDir` を「環境変数を読む関数」を引数に取る形にしたので、Notebook からは既定の場所だけを差し替えて使えます。

### 欠損値の分布

```kotlin
val missing = countMissing(df)
val missingDf = dataFrameOf("列", "欠損値の数")(*missing.flatMap { (k, v) -> listOf(k, v) }.toTypedArray())
missingDf.plot {
    bars {
        x("列")
        y("欠損値の数")
    }
    layout.title = "列ごとの欠損値の数"
}
```

棒グラフで見ると、欠損値は特徴量の 4 列に 1〜2 件ずつ散らばっていて、特定の列に偏っていません。

### 品種ごとの特徴量

```kotlin
val split = prepareIris(irisCsv, testSize = 0.3, seed = 0)
val train = split.xTrain.add("種類") { split.tTrain[index()] }
train.plot {
    points {
        x("花弁長さ")
        y("花弁幅")
        color("種類")
    }
    layout.title = "訓練データの花弁の長さと幅"
}
```

`add("種類") { split.tTrain[index()] }` は、行の位置（`index()`）に対応する正解ラベルを新しい列として加えます。探索には訓練データだけを使います。

```kotlin
train.groupBy("種類").mean()
```

品種ごとの平均値（小数第 3 位で四捨五入）は次のとおりです。

| 種類 | がく片長さ | がく片幅 | 花弁長さ | 花弁幅 |
|------|-----------|---------|---------|-------|
| Iris-versicolor | 0.459 | 0.328 | 0.544 | 0.523 |
| Iris-setosa | 0.193 | 0.606 | 0.253 | 0.061 |
| Iris-virginica | 0.608 | 0.417 | 0.701 | 0.770 |

散布図と平均値から、`Iris-setosa` は花弁長さ・花弁幅がほかの 2 品種よりはっきり小さいことが分かります。訓練データに入った行が Python 版と違うので値は少し異なりますが、読み取れる傾向は同じです。

グラフの画像は記事に載せていません。配布データの点をそのまま描いたグラフは、データの再配布に当たるおそれがあるためです。

### IDE なしで Notebook を実行する

Notebook の動作確認は、IntelliJ IDEA を使わずに Gradle のタスクからも行えます。[uv](https://docs.astral.sh/uv/) で Kotlin の Jupyter カーネル（kotlin-jupyter-kernel）を一時的に取得して実行し、出力を `build/notebooks/` に書き出します。

```bash
./gradlew notebookExecute
```

```text
実行: chapter02_iris_exploration.ipynb
BUILD SUCCESSFUL in 13s
```

### 出力セルを消してからコミットする

Notebook の出力セルにはデータが残るので、コミットの前に消します。出力が残っていないかは `check` タスクに組み込んだ `notebookVerify` が検査し、CI でも実行されます。

```bash
./gradlew notebookStrip
./gradlew notebookVerify
```

これらのタスクの作りは第 6 章で扱います。

<details>
<summary>この章の完成コード（src/main/kotlin/chapter02/IrisPreprocessing.kt）</summary>

```kotlin
package chapter02

import org.jetbrains.kotlinx.dataframe.AnyCol
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.fillNulls
import org.jetbrains.kotlinx.dataframe.api.remove
import org.jetbrains.kotlinx.dataframe.api.with
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.File
import kotlin.math.ceil
import kotlin.random.Random

fun loadIris(csvFile: File): AnyFrame = DataFrame.readCSV(csvFile)

private fun AnyCol.countNulls(): Int = values().count { it == null }

fun countMissing(df: AnyFrame): Map<String, Int> = df.columns().associate { it.name() to it.countNulls() }

fun columnMeans(
    df: AnyFrame,
    columns: List<String>,
): Map<String, Double> =
    columns.associateWith { name ->
        df[name]
            .values()
            .filterIsInstance<Number>()
            .map { it.toDouble() }
            .average()
    }

fun fillMissing(
    df: AnyFrame,
    values: Map<String, Double>,
): AnyFrame = values.entries.fold(df) { filled, (name, value) -> filled.fillNulls(name).with { value } }

fun splitFeaturesAndTarget(
    df: AnyFrame,
    target: String,
): Pair<AnyFrame, List<String>> = df.remove(target) to df[target].values().map { it.toString() }

data class TrainTestSplit(
    val xTrain: AnyFrame,
    val xTest: AnyFrame,
    val tTrain: List<String>,
    val tTest: List<String>,
)

fun splitTrainTest(
    x: AnyFrame,
    t: List<String>,
    testSize: Double,
    seed: Int,
): TrainTestSplit {
    val positions = (0 until x.rowsCount()).shuffled(Random(seed))
    val nTrain = x.rowsCount() - ceil(x.rowsCount() * testSize).toInt()
    val train = positions.take(nTrain)
    val test = positions.drop(nTrain)
    return TrainTestSplit(
        xTrain = x[train],
        xTest = x[test],
        tTrain = t.slice(train),
        tTest = t.slice(test),
    )
}

const val TARGET = "種類"

fun prepareIris(
    csvFile: File,
    testSize: Double,
    seed: Int,
): TrainTestSplit {
    val (x, t) = splitFeaturesAndTarget(loadIris(csvFile), TARGET)
    val split = splitTrainTest(x, t, testSize, seed)
    val means = columnMeans(split.xTrain, x.columnNames())
    return split.copy(
        xTrain = fillMissing(split.xTrain, means),
        xTest = fillMissing(split.xTest, means),
    )
}
```

</details>

## 2.13 まとめ

この章では、Kotlin DataFrame で欠損値を含むデータを前処理し、訓練データとテストデータに分けました。

1. **学習用テスト** — Kotlin DataFrame が BOM を取り除き、空欄を null として読み、`mean` が欠損値を除くことをテストで確かめた
2. **null 許容型** — 欠損値を含む列は `Double?` になり、`filterIsInstance<Number>()` で null を取り除いて平均を求めた
3. **不変のデータフレーム** — `fillNulls` は新しいデータフレームを返し、元のデータを変えないことをテストで約束した
4. **三角測量と再現性** — 件数と分け方の性質のテストを重ね、`Random(seed)` による再現性のあるシャッフルに一般化した
5. **拡張関数と Notebook** — 読みにくい処理を拡張関数で名前付けし、Notebook からはテスト済みの JAR を呼んで探索した

次の章では、ジニ不純度を使う決定木を自作し、JVM の機械学習ライブラリ Tribuo の決定木と結果を突き合わせます。
