# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが増えると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。そこで、`interface Classifier` でモデルに共通する操作（`fit` と `predict`）を定義し、どのモデルも同じ関数で評価できるようにします。最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作します。

[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md) と同じ TODO リストで進めます。Kotlin 版では、次の 2 点に注目してください。

- Python の `Protocol` は「メソッドを持っていればよい」という構造的な型でしたが、Kotlin の `interface` は **実装を宣言したクラスだけ** を受け入れます。第 3 章の `DecisionTree` を変更せずに共通のインターフェースに合わせる方法を考えます
- NumPy の行列計算が無いので、ロジスティック回帰の勾配降下法を `DoubleArray` と添字で書きます

ライブラリとの突き合わせには、Tribuo の `LogisticRegressionTrainer` と `RandomForestTrainer` を使います。最適化の方法や乱数の使い方が自作と違うので、予測の完全一致は求めず、正解率を比べます（[ADR 002](../../../adr/002-kotlin-ml-libraries.md)）。Tribuo のトレーナーも、自作のモデルと同じ `Classifier` として評価できるようにします。

データは第 2 章・第 3 章と同じ iris を使い、第 2 章の `prepareIris` で前処理します。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 値がすべて同じなら確率は均等になる
  - [ ] 値の差が指数の比になる
  - [ ] 大きな値でもあふれない
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 2 種類・3 種類のラベルを予測する
  - [ ] 学習を繰り返すと損失が小さくなる
- [ ] ランダムフォレストを学習して予測する
  - [ ] 多数決で予測を 1 つに決める
  - [ ] ブートストラップ標本を選ぶ
  - [ ] 第 3 章の決定木を再利用する
- [ ] 特徴量の重要度を計算する
  - [ ] 決定木 1 本の重要度
  - [ ] ランダムフォレストの重要度
- [ ] どのモデルも同じ関数で評価する
  - [ ] 第 3 章の決定木を変更せずに共通のインターフェースに合わせる
  - [ ] Tribuo のトレーナーも同じ関数で評価する
- [ ] 実データで Tribuo と正解率を突き合わせる

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和 + 切片」を計算し、それを **ソフトマックス関数** で確率に変換します。

```text
スコア(品種 k) = w(k, がく片長さ) × がく片長さ + … + w(k, 花弁幅) × 花弁幅 + b(k)
確率(品種 k)   = exp(スコア(品種 k)) / Σ exp(スコア(品種 j))
```

多クラスへの広げ方には、品種ごとに「その品種か、それ以外か」の 2 値分類器を作る one-vs-rest と、この章のように 1 つのモデルで全品種の確率を同時に求めるソフトマックス（多項ロジスティック回帰）があります。ソフトマックスを選んだのは、3 品種の確率の合計が必ず 1 になり、確率として解釈しやすいからです。

### 仮実装

Python 版は、1 行が 1 サンプルの 2 次元配列をまとめて変換しました。Kotlin 版には NumPy のような配列の一括計算が無いので、**1 サンプル分のスコア**（`DoubleArray`）を受け取る関数にします。複数のサンプルは、呼び出し側で `map` します。

```kotlin
// src/test/kotlin/chapter10/LogisticRegressionTest.kt
package chapter10

import kotlin.test.Test
import kotlin.test.assertEquals

class SoftmaxTest {
    @Test
    fun `値がすべて同じなら確率は均等になる`() {
        assertEquals(listOf(0.25, 0.25, 0.25, 0.25), softmax(doubleArrayOf(0.0, 0.0, 0.0, 0.0)).toList())
    }
}
```

`DoubleArray` の `equals` は中身ではなく同じ配列かどうかを比べるので、`toList()` でリストにしてから比べています。

```text
> Task :compileTestKotlin FAILED
e: .../src/test/kotlin/chapter10/LogisticRegressionTest.kt:9:54 Unresolved reference 'softmax'.
```

均等な確率を返す仮実装で Green にします。

```kotlin
// src/main/kotlin/chapter10/LogisticRegression.kt
package chapter10

fun softmax(z: DoubleArray): DoubleArray = DoubleArray(z.size) { 1.0 / z.size }
```

`DoubleArray(大きさ) { 添字 -> 値 }` は、要素を関数で作る配列の作り方です。

### 三角測量

値の差が `ln 2` なら、確率の比は 2 倍になるはずです。

```kotlin
    @Test
    fun `値の差が指数の比になる`() {
        val probabilities = softmax(doubleArrayOf(0.0, ln(2.0)))

        assertEquals(1.0 / 3, probabilities[0], absoluteTolerance = 1e-12)
        assertEquals(2.0 / 3, probabilities[1], absoluteTolerance = 1e-12)
    }
```

```text
SoftmaxTest > 値がすべて同じなら確率は均等になる() PASSED
SoftmaxTest > 値の差が指数の比になる() FAILED
    org.opentest4j.AssertionFailedError: Expected <0.3333333333333333> with absolute tolerance <1.0E-12>, actual <0.5>.
2 tests completed, 1 failed
```

定義どおりに一般化します。

```kotlin
import kotlin.math.exp

fun softmax(z: DoubleArray): DoubleArray {
    val exps = z.map { exp(it) }
    val total = exps.sum()
    return exps.map { it / total }.toDoubleArray()
}
```

### 大きな値でもあふれない

学習の途中では、スコアが大きな値になることがあります。

```kotlin
    @Test
    fun `大きな値でもあふれずに確率を求める`() {
        assertEquals(listOf(0.5, 0.5), softmax(doubleArrayOf(1000.0, 1000.0)).toList())
    }
```

```text
SoftmaxTest > 大きな値でもあふれずに確率を求める() FAILED
    org.opentest4j.AssertionFailedError: expected: <[0.5, 0.5]> but was: <[NaN, NaN]>
SoftmaxTest > 値がすべて同じなら確率は均等になる() PASSED
SoftmaxTest > 値の差が指数の比になる() PASSED
3 tests completed, 1 failed
```

`exp(1000.0)` は `Double` で表せる範囲を超えて `Infinity` になり、`Infinity / Infinity` が `NaN`（非数）になりました。Python 版の NumPy は `RuntimeWarning` を出しましたが、Kotlin（JVM）の浮動小数点数の計算は警告も例外も出さず、`NaN` を黙って返します。境界の値のテストが無ければ気づけません。

ソフトマックスは、すべての値から同じ数を引いても結果が変わりません（分子と分母に同じ `exp(-c)` が掛かるため）。そこで最大値を引いてから `exp` を計算します。

```kotlin
fun softmax(z: DoubleArray): DoubleArray {
    val max = z.max()
    val exps = z.map { exp(it - max) }
    val total = exps.sum()
    return exps.map { it / total }.toDoubleArray()
}
```

```text
SoftmaxTest > 大きな値でもあふれずに確率を求める() PASSED
SoftmaxTest > 値がすべて同じなら確率は均等になる() PASSED
SoftmaxTest > 値の差が指数の比になる() PASSED
```

## 10.4 ロジスティック回帰

### 仮実装と三角測量

第 3 章の決定木と同じく `fit` と `predict` を持つクラスにします。1 種類のラベルだけを学習する例は、最初のラベルを返す仮実装で通ります。

```kotlin
class LogisticRegressionTest {
    @Test
    fun `1種類のラベルだけを学習するとそのラベルを予測する`() {
        val x = dataFrameOf("花弁幅" to listOf(0.1, 0.2))
        val t = listOf("setosa", "setosa")

        val model = LogisticRegression().fit(x, t)

        assertEquals(listOf("setosa", "setosa"), model.predict(dataFrameOf("花弁幅" to listOf(0.15, 0.9))))
    }
}
```

```text
> Task :compileTestKotlin FAILED
e: .../src/test/kotlin/chapter10/LogisticRegressionTest.kt:34:21 Unresolved reference 'LogisticRegression'.
```

```kotlin
class LogisticRegression {
    private var label = ""

    fun fit(
        x: AnyFrame,
        t: List<String>,
    ): LogisticRegression {
        label = t.first()
        return this
    }

    fun predict(x: AnyFrame): List<String> = List(x.rowsCount()) { label }
}
```

2 種類のラベルを境界の左右で予測する例を加えると、仮実装では通りません。

```kotlin
    @Test
    fun `2種類のラベルを境界の左右で予測する`() {
        val x = dataFrameOf("花弁幅" to listOf(0.1, 0.2, 0.8, 0.9))
        val t = listOf("setosa", "setosa", "virginica", "virginica")

        val model = LogisticRegression().fit(x, t)

        assertEquals(listOf("setosa", "virginica"), model.predict(dataFrameOf("花弁幅" to listOf(0.15, 0.85))))
    }
```

```text
LogisticRegressionTest > 2種類のラベルを境界の左右で予測する() FAILED
    org.opentest4j.AssertionFailedError: expected: <[setosa, virginica]> but was: <[setosa, setosa]>
LogisticRegressionTest > 1種類のラベルだけを学習するとそのラベルを予測する() PASSED
```

### 勾配降下法で学習する

重みを少しずつ動かして、予測した確率を正解に近づけます。損失（交差エントロピー）を小さくする方向は、「予測した確率 − 正解」から計算できます。正解の品種を 1、それ以外を 0 とした表（one-hot 表現）を作る代わりに、**正解の品種の確率からだけ 1 を引けば**、「確率 − 正解」になります。

```kotlin
fun AnyFrame.toRows(): List<DoubleArray> {
    val columns = columnNames().map { name -> this[name].values().map { (it as Number).toDouble() } }
    return List(rowsCount()) { row -> DoubleArray(columns.size) { columns[it][row] } }
}

class LogisticRegression(
    private val learningRate: Double = 1.0,
    private val epochs: Int = 5000,
) {
    var classes: List<String> = emptyList()
        private set

    // weights[特徴量][品種]
    var weights: Array<DoubleArray> = emptyArray()
        private set

    var bias: DoubleArray = DoubleArray(0)
        private set

    private fun scores(row: DoubleArray): DoubleArray =
        DoubleArray(classes.size) { k -> row.indices.sumOf { f -> row[f] * weights[f][k] } + bias[k] }

    fun fit(
        x: AnyFrame,
        t: List<String>,
    ): LogisticRegression {
        val rows = x.toRows()
        classes = t.distinct().sorted()
        val targets = t.map { classes.indexOf(it) }
        weights = Array(x.columnsCount()) { DoubleArray(classes.size) }
        bias = DoubleArray(classes.size)
        repeat(epochs) {
            // 確率 − 正解（正解の品種だけ 1 を引く）
            val errors = rows.indices.map { i -> softmax(scores(rows[i])).also { it[targets[i]] -= 1.0 } }
            for (k in classes.indices) {
                for (f in weights.indices) {
                    weights[f][k] -= learningRate * rows.indices.sumOf { i -> rows[i][f] * errors[i][k] } / rows.size
                }
                bias[k] -= learningRate * errors.sumOf { it[k] } / rows.size
            }
        }
        return this
    }

    fun predict(x: AnyFrame): List<String> =
        x.toRows().map { row ->
            val scores = scores(row)
            classes[scores.indices.maxBy { scores[it] }]
        }
}
```

- `AnyFrame.toRows()` は、データフレームを 1 行 1 つの `DoubleArray` のリストに変換する拡張関数です。列ごとに値を取り出してから、行ごとに組み直しています
- Python 版の `features @ self.weights`（行列の積）は、`scores` の `sumOf` で 1 サンプルずつ計算しています。1 回の繰り返しで、全サンプルの誤差を先に求めてから重みを更新するので、Python 版と同じ **バッチ勾配降下法** です
- `weights` は `Array<DoubleArray>`（配列の配列）で、`weights[特徴量][品種]` の順に添字を付けます。どちらの添字を先にするかは型から分からないので、コメントで残しています
- `softmax(...).also { it[targets[i]] -= 1.0 }` の `also` は、受け取ったオブジェクトに処理をしてから、そのオブジェクトを返すスコープ関数です。新しく作った確率の配列を、その場で「確率 − 正解」に書き換えています
- `predict` では確率を計算せず、スコアが最大の品種を選びます。ソフトマックスは大小関係を変えないので、結果は同じです

```text
LogisticRegressionTest > 2種類のラベルを境界の左右で予測する() PASSED
LogisticRegressionTest > 1種類のラベルだけを学習するとそのラベルを予測する() PASSED
```

### 損失の記録と、学習前の予測

3 品種・2 特徴量の例と、損失が下がることと、学習前の予測がエラーになることを確かめます。

```kotlin
    @Test
    fun `3種類のラベルを2つの特徴量から予測する`() {
        val x =
            dataFrameOf(
                "花弁長さ" to listOf(0.1, 0.2, 0.5, 0.6, 0.5, 0.6),
                "花弁幅" to listOf(0.1, 0.2, 0.1, 0.2, 0.8, 0.9),
            )
        val t = listOf("setosa", "setosa", "versicolor", "versicolor", "virginica", "virginica")

        val model = LogisticRegression().fit(x, t)

        assertEquals(t, model.predict(x))
    }

    @Test
    fun `学習を繰り返すと損失が小さくなる`() {
        val x = dataFrameOf("花弁幅" to listOf(0.1, 0.2, 0.8, 0.9))
        val t = listOf("setosa", "setosa", "virginica", "virginica")

        val model = LogisticRegression(epochs = 100).fit(x, t)

        assertEquals(100, model.losses.size)
        assertTrue(model.losses.last() < model.losses.first())
    }

    @Test
    fun `学習する前に予測するとエラーになる`() {
        val error = assertFailsWith<IllegalStateException> { LogisticRegression().predict(dataFrameOf("花弁幅" to listOf(0.1))) }

        assertEquals("fit で学習してから predict を呼んでください", error.message)
    }
```

`losses` がまだ無いので、テストがコンパイルできません。

```text
> Task :compileTestKotlin FAILED
e: .../src/test/kotlin/chapter10/LogisticRegressionTest.kt:72:33 Unresolved reference 'losses' on receiver of type 'LogisticRegression'.
e: .../src/test/kotlin/chapter10/LogisticRegressionTest.kt:73:26 Unresolved reference 'losses' on receiver of type 'LogisticRegression'.
e: .../src/test/kotlin/chapter10/LogisticRegressionTest.kt:73:40 'operator' modifier is required on 'fun String.compareTo(other: String, ignoreCase: Boolean = ...): Int'.
e: .../src/test/kotlin/chapter10/LogisticRegressionTest.kt:73:48 Unresolved reference 'losses' on receiver of type 'LogisticRegression'.
```

3 行目は、`losses` の型が分からないため `<` の比較を解決できなかったという、第 2 章でも見た連鎖的なエラーです。

損失を記録するようにします。確率は損失の計算と誤差の計算の両方で使うので、先に全サンプル分を求めておき、誤差は `copyOf()` で写してから書き換えます。

```kotlin
private const val EPSILON = 1e-12

fun crossEntropy(
    probabilities: List<DoubleArray>,
    targets: List<Int>,
): Double = -probabilities.indices.sumOf { i -> ln(probabilities[i][targets[i]] + EPSILON) } / probabilities.size
```

```kotlin
        val recorded = mutableListOf<Double>()
        repeat(epochs) {
            val probabilities = rows.map { softmax(scores(it)) }
            recorded += crossEntropy(probabilities, targets)
            // 確率 − 正解（正解の品種だけ 1 を引く）
            val errors = probabilities.mapIndexed { i, p -> p.copyOf().also { it[targets[i]] -= 1.0 } }
```

- 交差エントロピーは、正解の品種の確率の対数の平均にマイナスを付けたものです。one-hot 表現との積の和を取る Python 版と同じ値になります
- `EPSILON` は、確率が 0 のときに `ln(0.0)` が負の無限大にならないように足す小さな値です
- 損失は `recorded` に集めてから、学習の最後に `losses = recorded` で公開します。`losses` の型は読み取り専用の `List<Double>` なので、クラスの外からは損失の記録を書き換えられません

これで損失のテストは通りましたが、学習前の予測は、期待した例外と違う例外で失敗しました。

```text
LogisticRegressionTest > 学習する前に予測するとエラーになる() FAILED
    org.opentest4j.AssertionFailedError: Expected an exception of class java.lang.IllegalStateException to be thrown, but was java.util.NoSuchElementException
        java.util.NoSuchElementException
LogisticRegressionTest > 2種類のラベルを境界の左右で予測する() PASSED
LogisticRegressionTest > 学習を繰り返すと損失が小さくなる() PASSED
LogisticRegressionTest > 3種類のラベルを2つの特徴量から予測する() PASSED
LogisticRegressionTest > 1種類のラベルだけを学習するとそのラベルを予測する() PASSED
```

学習前は品種が 1 つも無いので、空のスコアに `maxBy` を呼んで `NoSuchElementException` になっていました。エラーは起きていますが、読み手には原因が分かりません。第 3 章の決定木と同じメッセージの `IllegalStateException` を出すようにします。

```kotlin
    fun predict(x: AnyFrame): List<String> {
        check(classes.isNotEmpty()) { "fit で学習してから predict を呼んでください" }
        return x.toRows().map { row ->
            val scores = scores(row)
            classes[scores.indices.maxBy { scores[it] }]
        }
    }
```

`check(条件) { メッセージ }` は、条件が偽なら `IllegalStateException` を投げる標準ライブラリの関数です。第 3 章の `checkNotNull` の、null 以外の条件版です。

```text
LogisticRegressionTest > 学習する前に予測するとエラーになる() PASSED
LogisticRegressionTest > 2種類のラベルを境界の左右で予測する() PASSED
LogisticRegressionTest > 学習を繰り返すと損失が小さくなる() PASSED
LogisticRegressionTest > 3種類のラベルを2つの特徴量から予測する() PASSED
LogisticRegressionTest > 1種類のラベルだけを学習するとそのラベルを予測する() PASSED
```

## 10.5 ランダムフォレスト

### 仕組み

ランダムフォレストは、次の 2 つの「ばらつき」を持たせた決定木をたくさん作り、予測を多数決で決めます。

1. **ブートストラップ標本**: 訓練データから、同じ件数を重複を許して選び直したデータで木を学習する（バギング）
2. **特徴量の部分集合**: 木ごとに使う特徴量を一部だけに絞る

1 本 1 本の決定木は訓練データの細部を覚えて過学習しがちですが、違うデータ・違う特徴量で学習した木の多数決を取ると、個々の木の癖が打ち消し合います。

この章では、第 3 章の `DecisionTree` を **変更せずに** 再利用します。そのため、特徴量の絞り込みは「木ごと」に行います。Tribuo の `RandomForestTrainer` は「分割ごと」に特徴量を選び直すので、仕組みは少し異なります（10.8 節）。

### 多数決

```kotlin
// src/test/kotlin/chapter10/RandomForestTest.kt
class MajorityVoteTest {
    @Test
    fun `サンプルごとに最も多い予測を選ぶ`() {
        val votes =
            listOf(
                listOf("setosa", "virginica"),
                listOf("setosa", "virginica"),
                listOf("versicolor", "setosa"),
            )

        assertEquals(listOf("setosa", "virginica"), majorityVote(votes))
    }
}
```

`votes` は「木ごとの予測のリスト」です。サンプルごとの多数決に組み替えるだけなので、次のブートストラップ標本のテストと一緒に書き、明白な実装で進めます。

### ブートストラップ標本

```kotlin
class BootstrapSampleTest {
    @Test
    fun `元のデータと同じ件数の行番号を重複を許して選ぶ`() {
        val rows = bootstrapSample(100, Random(0))

        assertEquals(100, rows.size)
        assertTrue(rows.all { it in 0 until 100 })
        assertTrue(rows.toSet().size < 100)
    }

    @Test
    fun `同じシードなら同じ行を選ぶ`() {
        assertEquals(bootstrapSample(10, Random(42)), bootstrapSample(10, Random(42)))
    }
}
```

```text
> Task :compileTestKotlin FAILED
e: .../src/test/kotlin/chapter10/RandomForestTest.kt:18:53 Unresolved reference 'majorityVote'.
e: .../src/test/kotlin/chapter10/RandomForestTest.kt:25:20 Unresolved reference 'bootstrapSample'.
e: .../src/test/kotlin/chapter10/RandomForestTest.kt:28:31 Unresolved reference 'it'.
e: .../src/test/kotlin/chapter10/RandomForestTest.kt:29:33 Unresolved reference 'size'.
e: .../src/test/kotlin/chapter10/RandomForestTest.kt:34:9 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: .../src/test/kotlin/chapter10/RandomForestTest.kt:34:22 Unresolved reference 'bootstrapSample'.
e: .../src/test/kotlin/chapter10/RandomForestTest.kt:34:55 Unresolved reference 'bootstrapSample'.
```

```kotlin
// src/main/kotlin/chapter10/RandomForest.kt
fun majorityVote(votes: List<List<String>>): List<String> =
    votes.first().indices.map { sample ->
        votes
            .map { it[sample] }
            .groupingBy { it }
            .eachCount()
            .maxBy { it.value }
            .key
    }

fun bootstrapSample(
    size: Int,
    random: Random,
): List<Int> = List(size) { random.nextInt(size) }
```

- `majorityVote` は、サンプルの番号ごとに各木の予測を集め（`votes.map { it[sample] }`）、第 3 章の葉の多数決と同じ `groupingBy`・`eachCount`・`maxBy` で最も多いラベルを選びます。Python 版の `zip(*votes)`（行と列の入れ替え）を、添字で書いた形です
- `random.nextInt(size)` は 0 以上 `size` 未満の整数を返します。乱数生成器を引数で受け取るのは、森全体で 1 つの生成器を使い回し、シード 1 つで全部の木の乱数を再現できるようにするためです

### 森を作る

```kotlin
internal fun twoSpecies(): Pair<AnyFrame, List<String>> {
    val x =
        dataFrameOf(
            "がく片幅" to listOf(0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4, 0.5, 0.3),
            "花弁幅" to listOf(0.1, 0.12, 0.14, 0.16, 0.18, 0.8, 0.82, 0.84, 0.86, 0.88),
        )
    val t = List(5) { "setosa" } + List(5) { "virginica" }
    return x to t
}

class RandomForestTest {
    @Test
    fun `指定した数だけ第3章の決定木を学習する`() {
        val (x, t) = twoSpecies()

        val model = RandomForest(nEstimators = 5, maxFeatures = 1, seed = 0).fit(x, t)

        assertEquals(5, model.trees.size)
    }

    @Test
    fun `各決定木は指定した数の特徴量だけを使う`() {
        val (x, t) = twoSpecies()

        val model = RandomForest(nEstimators = 5, maxFeatures = 1, seed = 0).fit(x, t)

        assertTrue(model.trees.all { it.columns.size == 1 })
    }

    @Test
    fun `決定木の多数決で予測する`() {
        val (x, t) = twoSpecies()

        val model = RandomForest(nEstimators = 25, maxFeatures = 2, seed = 0).fit(x, t)

        val newX =
            dataFrameOf(
                "がく片幅" to listOf(0.4, 0.4),
                "花弁幅" to listOf(0.13, 0.83),
            )
        assertEquals(listOf("setosa", "virginica"), model.predict(newX))
    }

    @Test
    fun `同じシードなら同じ予測になる`() {
        val (x, t) = twoSpecies()

        val first = RandomForest(nEstimators = 5, maxFeatures = 1, seed = 7).fit(x, t)
        val second = RandomForest(nEstimators = 5, maxFeatures = 1, seed = 7).fit(x, t)

        assertEquals(first.trees.map { it.columns }, second.trees.map { it.columns })
        assertEquals(first.predict(x), second.predict(x))
    }

    @Test
    fun `学習する前に予測するとエラーになる`() {
        val error = assertFailsWith<IllegalStateException> { RandomForest().predict(dataFrameOf("花弁幅" to listOf(0.1))) }

        assertEquals("fit で学習してから predict を呼んでください", error.message)
    }
}
```

Python 版の最初のテストは、各木が `DecisionTree` であることも `isinstance` で確かめていました。Kotlin 版では、`trees` の要素の型に `DecisionTree` と書くので、コンパイラがそれを保証します。テストで確かめるのは件数だけにしました。

```text
> Task :compileTestKotlin FAILED
e: .../src/test/kotlin/chapter10/RandomForestTest.kt:56:21 Unresolved reference 'RandomForest'.
e: .../src/test/kotlin/chapter10/RandomForestTest.kt:65:21 Unresolved reference 'RandomForest'.
...
```

部品（多数決・ブートストラップ標本・第 3 章の決定木）がそろっているので、組み立てるだけの明白な実装で進めます。

```kotlin
data class FittedTree(
    val columns: List<String>,
    val rows: List<Int>,
    val model: DecisionTree,
)

class RandomForest(
    private val nEstimators: Int = 10,
    private val maxFeatures: Int = 2,
    private val maxDepth: Int? = null,
    private val seed: Int = 0,
) {
    var trees: List<FittedTree> = emptyList()
        private set

    fun fit(
        x: AnyFrame,
        t: List<String>,
    ): RandomForest {
        val random = Random(seed)
        trees =
            List(nEstimators) {
                val rows = bootstrapSample(x.rowsCount(), random)
                val chosen = x.columnNames().shuffled(random).take(maxFeatures).toSet()
                val columns = x.columnNames().filter { it in chosen }
                val model = DecisionTree(maxDepth).fit(x[rows].select(*columns.toTypedArray()), t.slice(rows))
                FittedTree(columns = columns, rows = rows, model = model)
            }
        return this
    }

    fun predict(x: AnyFrame): List<String> {
        check(trees.isNotEmpty()) { "fit で学習してから predict を呼んでください" }
        return majorityVote(trees.map { it.model.predict(x.select(*it.columns.toTypedArray())) })
    }
}
```

- Python 版は「使った列と学習した木」の組をタプルで、行番号を別のリストで持ちましたが、Kotlin 版では 1 本分の情報を data class `FittedTree` にまとめました。`it.columns` のように名前で読めます
- `shuffled(random).take(maxFeatures)` で、重複なしに列を選びます。`filter { it in chosen }` で元の列の順に戻しておくと、同じ組み合わせが同じ並びになります
- `x[rows]` は、行番号のリストで行を取り出します。ブートストラップ標本のように同じ行番号が重複していても、その回数だけ行が並びます
- `select(*配列)` は、指定した列だけのデータフレームを作ります。`*` は配列を可変長引数に展開するスプレッド演算子です（10.9 節で書き直します）
- ブートストラップ標本の行番号は、特徴量の重要度を計算するときに使うので `rows` にも残します（10.6 節）

```text
RandomForestTest > 学習する前に予測するとエラーになる() PASSED
RandomForestTest > 指定した数だけ第3章の決定木を学習する() PASSED
RandomForestTest > 同じシードなら同じ予測になる() PASSED
RandomForestTest > 各決定木は指定した数の特徴量だけを使う() PASSED
RandomForestTest > 決定木の多数決で予測する() PASSED
```

第 3 章の `DecisionTree` には一切手を入れていません。`fit` と `predict` という小さな公開 API で作ってあったので、部品としてそのまま組み込めました。

## 10.6 特徴量の重要度

### 計算方法

決定木の各節で、

```text
減少量 = その節に届いた件数 × (その節のジニ不純度 − 分割後のジニ不純度)
```

を求め、分割に使った特徴量ごとに合計し、全体が 1 になるように割合にします。第 3 章の `Split.impurity` は「分割後のジニ不純度（件数で重み付けした平均）」なので、そのまま使えます。

ただし、第 3 章の `Node` は、その節に届いた件数を持っていません。そこで、学習に使ったデータをもう一度木に流して、節ごとに件数とジニ不純度を求めます。

第 3 章で確かめたとおり、Tribuo の決定木の `getTopFeatures` は分割に使われた回数を返すので、不純度の減少量による重要度とは別の尺度です。この章では重要度を自作し、手で計算した値でテストします。

### 決定木 1 本の重要度

```kotlin
// src/test/kotlin/chapter10/FeatureImportanceTest.kt
class TreeImportancesTest {
    @Test
    fun `分割しない木はすべての特徴量の重要度が0`() {
        val x =
            dataFrameOf(
                "がく片幅" to listOf(0.3, 0.5),
                "花弁幅" to listOf(0.1, 0.2),
            )
        val t = listOf("setosa", "setosa")

        assertEquals(mapOf("がく片幅" to 0.0, "花弁幅" to 0.0), treeImportances(Leaf(label = "setosa"), x, t))
    }
}
```

```text
> Task :compileTestKotlin FAILED
e: .../src/test/kotlin/chapter10/FeatureImportanceTest.kt:19:58 Unresolved reference 'treeImportances'.
```

すべて 0 を返す仮実装で通ります。

```kotlin
// src/main/kotlin/chapter10/FeatureImportance.kt
fun treeImportances(
    tree: Tree,
    x: AnyFrame,
    t: List<String>,
): Map<String, Double> = x.columnNames().associateWith { 0.0 }
```

次に、花弁幅だけで分割する木と、2 つの特徴量で 2 回分割する木を例にします。

```kotlin
    @Test
    fun `1回だけ分割する木は分割に使った特徴量の重要度が1`() {
        val x =
            dataFrameOf(
                "がく片幅" to listOf(0.3, 0.5, 0.4, 0.6),
                "花弁幅" to listOf(0.1, 0.2, 0.8, 0.9),
            )
        val t = listOf("setosa", "setosa", "virginica", "virginica")
        val tree = checkNotNull(DecisionTree().fit(x, t).tree)

        assertEquals(mapOf("がく片幅" to 0.0, "花弁幅" to 1.0), treeImportances(tree, x, t))
    }

    @Test
    fun `分割で減った不純度を件数で重み付けして割合にする`() {
        val x =
            dataFrameOf(
                "花弁長さ" to listOf(0.1, 0.2, 0.3, 0.8, 0.7, 0.9),
                "花弁幅" to listOf(0.1, 0.1, 0.1, 0.2, 0.9, 0.9),
            )
        val t = listOf("setosa", "setosa", "setosa", "versicolor", "virginica", "virginica")
        val tree = checkNotNull(DecisionTree().fit(x, t).tree)

        val importances = treeImportances(tree, x, t)

        assertEquals(7.0 / 11, importances.getValue("花弁長さ"), absoluteTolerance = 1e-12)
        assertEquals(4.0 / 11, importances.getValue("花弁幅"), absoluteTolerance = 1e-12)
    }
```

2 つ目の例の期待値は、手で計算して決めました。

| 節 | 件数 | 節のジニ不純度 | 分割 | 分割後のジニ不純度 | 減少量 |
|----|------|--------------|------|-----------------|--------|
| 根 | 6 | 1 − (9 + 1 + 4) / 36 = 11/18 | 花弁長さ ≤ 0.5（setosa 3 件と残り 3 件） | 3/6 × 0 + 3/6 × 4/9 = 2/9 | 6 × (11/18 − 2/9) = 7/3 |
| 右の子 | 3 | 1 − (1 + 4) / 9 = 4/9 | 花弁幅 ≤ 0.55（花弁長さでは分け切れない） | 0 | 3 × 4/9 = 4/3 |

合計 11/3 に対する割合で、花弁長さが 7/11、花弁幅が 4/11 になります。

```text
TreeImportancesTest > 分割しない木はすべての特徴量の重要度が0() PASSED
TreeImportancesTest > 分割で減った不純度を件数で重み付けして割合にする() FAILED
    org.opentest4j.AssertionFailedError: Expected <0.6363636363636364> with absolute tolerance <1.0E-12>, actual <0.0>.
TreeImportancesTest > 1回だけ分割する木は分割に使った特徴量の重要度が1() FAILED
    org.opentest4j.AssertionFailedError: expected: <{がく片幅=0.0, 花弁幅=1.0}> but was: <{がく片幅=0.0, 花弁幅=0.0}>
3 tests completed, 2 failed
```

木をたどりながら「特徴量と減少量の組」を集め、特徴量ごとに合計して割合に直します。

```kotlin
private fun impurityDecreases(
    tree: Tree,
    x: AnyFrame,
    t: List<String>,
): List<Pair<String, Double>> =
    when (tree) {
        is Leaf -> emptyList()
        is Node -> {
            val split = tree.split
            val goesLeft = x[split.feature].values().map { (it as Number).toDouble() <= split.threshold }
            val left = goesLeft.indices.filter { goesLeft[it] }
            val right = goesLeft.indices.filterNot { goesLeft[it] }
            listOf(split.feature to t.size * (gini(t) - split.impurity)) +
                impurityDecreases(tree.left, x[left], t.slice(left)) +
                impurityDecreases(tree.right, x[right], t.slice(right))
        }
    }

fun treeImportances(
    tree: Tree,
    x: AnyFrame,
    t: List<String>,
): Map<String, Double> {
    val decreases = impurityDecreases(tree, x, t)
    val totals = x.columnNames().associateWith { feature -> decreases.filter { it.first == feature }.sumOf { it.second } }
    val total = totals.values.sum()
    return if (total == 0.0) totals else totals.mapValues { it.value / total }
}
```

- データを左右に振り分ける部分は、第 3 章の `buildTree` と同じ形の再帰です。`sealed interface Tree` の `when` で、葉と節を場合分けしています
- Python 版は合計用の辞書を引数で渡して書き換えましたが、Kotlin 版は再帰の結果を `List` の `+` でつなぎ、書き換える変数を持たない形にしました
- `associateWith` は、キー（列名）ごとに値を計算した `Map` を作ります。Kotlin の `associateWith` が作る `Map` はキーを追加した順を保つので、重要度も列の順に並びます

```text
TreeImportancesTest > 分割しない木はすべての特徴量の重要度が0() PASSED
TreeImportancesTest > 分割で減った不純度を件数で重み付けして割合にする() PASSED
TreeImportancesTest > 1回だけ分割する木は分割に使った特徴量の重要度が1() PASSED
```

### ランダムフォレストの重要度

森の重要度は、木ごとの重要度（学習に使ったブートストラップ標本で計算）の平均を、もう一度割合に直したものです。木が 1 本なら、その木の重要度と一致するはずです。

```kotlin
class ForestImportancesTest {
    @Test
    fun `木が1本なら学習に使った行でのその木の重要度と一致する`() {
        val x =
            dataFrameOf(
                "がく片幅" to listOf(0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4),
                "花弁幅" to listOf(0.1, 0.12, 0.14, 0.16, 0.8, 0.82, 0.84, 0.86),
            )
        val t = List(4) { "setosa" } + List(4) { "virginica" }
        val forest = RandomForest(nEstimators = 1, maxFeatures = 2, seed = 0).fit(x, t)
        val fitted = forest.trees.single()

        val expected = treeImportances(checkNotNull(fitted.model.tree), x[fitted.rows], t.slice(fitted.rows))

        assertEquals(expected, forestImportances(forest, x, t))
    }
}
```

`single()` は、要素がちょうど 1 つのリストからその要素を取り出します。要素が 0 個や 2 個以上なら例外になるので、「木が 1 本である」ことの確認も兼ねています。

```text
> Task :compileTestKotlin FAILED
e: .../src/test/kotlin/chapter10/FeatureImportanceTest.kt:66:32 Unresolved reference 'forestImportances'.
```

`treeImportances` と同じく「合計で割って割合にする」処理が必要になるので、`normalize` に切り出してから `forestImportances` を書きました。

```kotlin
fun treeImportances(
    tree: Tree,
    x: AnyFrame,
    t: List<String>,
): Map<String, Double> {
    val decreases = impurityDecreases(tree, x, t)
    val totals = x.columnNames().associateWith { feature -> decreases.filter { it.first == feature }.sumOf { it.second } }
    return normalize(totals)
}

private fun normalize(totals: Map<String, Double>): Map<String, Double> {
    val total = totals.values.sum()
    return if (total == 0.0) totals else totals.mapValues { it.value / total }
}

fun forestImportances(
    forest: RandomForest,
    x: AnyFrame,
    t: List<String>,
): Map<String, Double> {
    val perTree =
        forest.trees.map { fitted ->
            val tree = checkNotNull(fitted.model.tree)
            treeImportances(tree, x[fitted.rows].select(*fitted.columns.toTypedArray()), t.slice(fitted.rows))
        }
    val totals = x.columnNames().associateWith { feature -> perTree.sumOf { it[feature] ?: 0.0 } / forest.trees.size }
    return normalize(totals)
}
```

木ごとの重要度には、その木が使った列しか入っていません。`it[feature] ?: 0.0` で、使わなかった列は 0 として平均します。`Map` の `get`（`[]`）は、キーが無ければ null を返すので、エルビス演算子で既定値を補えます。

```text
ForestImportancesTest > 木が1本なら学習に使った行でのその木の重要度と一致する() PASSED
```

## 10.7 モデル共通のインターフェース

### interface で「fit と predict を持つもの」を表す

決定木・ロジスティック回帰・ランダムフォレストは、どれも `fit(x, t)` と `predict(x)` を持っています。これを `interface` で表します。

まず、テスト用の単純なモデル `AlwaysSetosa` で、評価関数の振る舞いを決めます。

```kotlin
// src/test/kotlin/chapter10/EvaluateTest.kt
private class AlwaysSetosa : Classifier {
    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): AlwaysSetosa = this

    override fun predict(x: AnyFrame): List<String> = List(x.rowsCount()) { "setosa" }
}

private fun smallSplit(): TrainTestSplit<String> =
    TrainTestSplit(
        xTrain = dataFrameOf("花弁幅" to listOf(0.1, 0.2, 0.8, 0.9)),
        xTest = dataFrameOf("花弁幅" to listOf(0.15, 0.25)),
        tTrain = listOf("setosa", "setosa", "virginica", "virginica"),
        tTest = listOf("setosa", "setosa"),
    )

class EvaluateTest {
    @Test
    fun `学習させてから訓練データとテストデータの正解率を求める`() {
        assertEquals(Score(train = 0.5, test = 1.0), evaluate(AlwaysSetosa(), smallSplit()))
    }
}
```

このテストファイルは、最初は `ClassifierTest.kt` という名前でした。ktlint の「1 つのクラスだけを持つファイルはクラス名と同じ名前にする」というルールで、`EvaluateTest.kt` に変えています（下の出力のファイル名は変える前のものです）。

```text
> Task :compileTestKotlin FAILED
e: .../src/test/kotlin/chapter10/ClassifierTest.kt:9:30 Unresolved reference 'Classifier'.
e: .../src/test/kotlin/chapter10/ClassifierTest.kt:29:9 Cannot infer type for type parameter 'T'. Specify it explicitly.
e: .../src/test/kotlin/chapter10/ClassifierTest.kt:29:22 Unresolved reference 'Score'.
e: .../src/test/kotlin/chapter10/ClassifierTest.kt:29:54 Unresolved reference 'evaluate'.
```

```kotlin
// src/main/kotlin/chapter10/Classifier.kt
interface Classifier {
    fun fit(
        x: AnyFrame,
        t: List<String>,
    ): Classifier

    fun predict(x: AnyFrame): List<String>
}

data class Score(
    val train: Double,
    val test: Double,
)

fun evaluate(
    model: Classifier,
    split: TrainTestSplit<String>,
): Score {
    model.fit(split.xTrain, split.tTrain)
    return Score(
        train = accuracy(model.predict(split.xTrain), split.tTrain),
        test = accuracy(model.predict(split.xTest), split.tTest),
    )
}
```

- 正解率は第 1 章の `accuracy`、分割結果は第 2 章の `TrainTestSplit<String>` を再利用しています
- `AlwaysSetosa` の `fit` の戻り値の型は、インターフェースの `Classifier` ではなく `AlwaysSetosa` です。Kotlin では、オーバーライドする関数の戻り値を、元の型の **サブタイプ** に狭められます。Python 版の `-> Self` と同じく、`AlwaysSetosa().fit(x, t)` の結果を `AlwaysSetosa` として使えます

```text
EvaluateTest > 学習させてから訓練データとテストデータの正解率を求める() PASSED
```

### 3 つのモデルを同じ関数で評価する

Python 版では、第 3 章の決定木と自作のモデルを `list[Classifier]` に入れるテストが、最初から通りました（`Protocol` は構造的部分型なので、`fit` と `predict` を持っていれば型が合います）。Kotlin ではそうはいかないことが分かっていたので、第 3 章の決定木を包むアダプター `DecisionTreeClassifier`（まだ無いクラス）を使う形でテストを書きました。

```kotlin
    @Test
    fun `第3章の決定木と自作のモデルを同じ関数で評価できる`() {
        val models: List<Classifier> =
            listOf(
                DecisionTreeClassifier(maxDepth = 1),
                LogisticRegression(),
                RandomForest(nEstimators = 5, maxFeatures = 1, seed = 0),
            )

        val scores = models.map { evaluate(it, smallSplit()) }

        assertEquals(List(3) { Score(train = 1.0, test = 1.0) }, scores)
    }
```

```text
> Task :compileTestKotlin FAILED
e: .../src/test/kotlin/chapter10/ClassifierTest.kt:34:38 Initializer type mismatch: expected 'List<Classifier>', actual 'List<Any>'.
e: .../src/test/kotlin/chapter10/ClassifierTest.kt:36:17 Unresolved reference 'DecisionTreeClassifier'.
```

2 つ目のエラーは、アダプターがまだ無いことです。1 つ目のエラーは、`LogisticRegression` と `RandomForest` も `List<Classifier>` に入らないことを示しています。念のため、アダプターの代わりに `chapter03.DecisionTree(maxDepth = 1)` をそのまま入れても確かめると、1 つ目と同じエラーだけが出ました。

```text
> Task :compileTestKotlin FAILED
e: .../src/test/kotlin/chapter10/ClassifierTest.kt:34:38 Initializer type mismatch: expected 'List<Classifier>', actual 'List<Any>'.
```

3 つのクラスはどれも `fit` と `predict` を持っていますが、どれも `Classifier` を実装すると **宣言していない** ので、共通の型は `Any` しかありません。Kotlin（と Java）のインターフェースは、名前で型を判定する **名前的部分型**（nominal subtyping）だからです。

`LogisticRegression` と `RandomForest` は、この章で書いたクラスなので、`: Classifier` を宣言して `override` を付ければ済みます。

```kotlin
class LogisticRegression(
    private val learningRate: Double = 1.0,
    private val epochs: Int = 5000,
) : Classifier {
    // ...
    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): LogisticRegression {
        // ...
    }

    override fun predict(x: AnyFrame): List<String> {
        // ...
    }
}
```

`RandomForest` も同じように `: Classifier` と `override` を付けました。

第 3 章の `DecisionTree` には、手を入れない方針です。そこで、`DecisionTree` を包んで `Classifier` として振る舞わせるクラス（**アダプター**）を書きます。

```kotlin
class DecisionTreeClassifier(
    maxDepth: Int? = null,
) : Classifier {
    private val tree = DecisionTree(maxDepth)

    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): DecisionTreeClassifier {
        tree.fit(x, t)
        return this
    }

    override fun predict(x: AnyFrame): List<String> = tree.predict(x)
}
```

```text
EvaluateTest > 第3章の決定木と自作のモデルを同じ関数で評価できる() PASSED
EvaluateTest > 学習させてから訓練データとテストデータの正解率を求める() PASSED
```

| 観点 | Python の `Protocol` | Kotlin の `interface` |
|------|--------------------|-----------------------|
| 型が合う条件 | 同じ名前・型のメソッドを持っている（構造的部分型） | 実装を宣言している（名前的部分型） |
| 既存のクラス（第 3 章の決定木） | そのまま入れられる | アダプターで包む |
| 型の確認 | mypy を実行したとき | コンパイルのとき |
| 利点 | 既存のコードに手を入れずに共通化できる | 「このクラスは `Classifier` として使う」という意図がコードに残り、メソッド名を変えると実装側でエラーになる |

Kotlin では、共通化のたびにアダプターが 1 つ増えます。一方で、`LogisticRegression` の `fit` の名前を変えると、`override` が何もオーバーライドしていないというエラーがクラスの定義の行で出ます。Python 版では、同じ誤りは `list[Classifier]` の型注釈を書いた行でしか見つかりません。

## 10.8 Tribuo のトレーナーを同じインターフェースで使う

### Tribuo のトレーナーを Classifier に包む

Tribuo では、学習の設定を持つ **トレーナー**（`Trainer<Label>`）が、データセットから **モデル**（`Model<Label>`）を作ります。トレーナーを受け取り、`fit` でモデルを作って持っておくアダプターを書けば、どのトレーナーも `Classifier` として評価できます。データフレームとの変換には、第 3 章の `toTribuoDataset` と `predictWithTribuo` をそのまま使います。

```kotlin
// src/test/kotlin/chapter10/TribuoClassifierTest.kt
class TribuoClassifierTest {
    private fun twoSpeciesSplit(): TrainTestSplit<String> {
        val (x, t) = twoSpecies()
        return TrainTestSplit(
            xTrain = x,
            xTest =
                dataFrameOf(
                    "がく片幅" to listOf(0.4, 0.4),
                    "花弁幅" to listOf(0.13, 0.83),
                ),
            tTrain = t,
            tTest = listOf("setosa", "virginica"),
        )
    }

    @Test
    fun `Tribuoのロジスティック回帰とランダムフォレストも同じ関数で評価できる`() {
        val models: List<Classifier> =
            listOf(
                TribuoClassifier(LogisticRegressionTrainer()),
                tribuoRandomForest(nEstimators = 10, maxDepth = null, seed = 0L),
            )

        val scores = models.map { evaluate(it, twoSpeciesSplit()) }

        assertEquals(List(2) { Score(train = 1.0, test = 1.0) }, scores)
    }

    @Test
    fun `学習する前に予測するとエラーになる`() {
        val error =
            assertFailsWith<IllegalStateException> {
                TribuoClassifier(LogisticRegressionTrainer()).predict(dataFrameOf("花弁幅" to listOf(0.1)))
            }

        assertEquals("fit で学習してから predict を呼んでください", error.message)
    }
}
```

```text
> Task :compileTestKotlin FAILED
e: .../src/test/kotlin/chapter10/TribuoClassifierTest.kt:29:17 Unresolved reference 'TribuoClassifier'.
e: .../src/test/kotlin/chapter10/TribuoClassifierTest.kt:30:17 Unresolved reference 'tribuoRandomForest'.
e: .../src/test/kotlin/chapter10/TribuoClassifierTest.kt:42:17 Unresolved reference 'TribuoClassifier'.
```

```kotlin
// src/main/kotlin/chapter10/TribuoClassifier.kt
class TribuoClassifier(
    private val trainer: Trainer<Label>,
) : Classifier {
    private var model: Model<Label>? = null

    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): TribuoClassifier {
        model = trainer.train(toTribuoDataset(x, t))
        return this
    }

    override fun predict(x: AnyFrame): List<String> {
        val fitted = checkNotNull(model) { "fit で学習してから predict を呼んでください" }
        return predictWithTribuo(fitted, x)
    }
}

// 分割ごとに半分の特徴量から選ぶ決定木を、ブートストラップ標本で nEstimators 本学習して多数決する
private const val FRACTION_FEATURES_IN_SPLIT = 0.5f

fun tribuoRandomForest(
    nEstimators: Int,
    maxDepth: Int?,
    seed: Long,
): TribuoClassifier {
    val tree = CARTClassificationTrainer(maxDepth ?: Int.MAX_VALUE, FRACTION_FEATURES_IN_SPLIT, seed)
    return TribuoClassifier(RandomForestTrainer(tree, VotingCombiner(), nEstimators, seed))
}
```

- `private var model: Model<Label>? = null` は、学習前は null のモデルです。`predict` では第 3 章と同じく `checkNotNull` で、null なら分かりやすいメッセージの例外にします
- `RandomForestTrainer` は、内側の決定木のトレーナー・予測をまとめる方法（多数決の `VotingCombiner`）・木の数・シードを受け取ります

```text
TribuoClassifierTest > 学習する前に予測するとエラーになる() PASSED
TribuoClassifierTest > Tribuoのロジスティック回帰とランダムフォレストも同じ関数で評価できる() PASSED
```

### Tribuo の設定と自作との違い

Tribuo のソースコードとクラスのコンストラクターを確かめると、既定の設定は自作と次の点が違います。

| 項目 | 自作 | Tribuo |
|------|------|--------|
| ロジスティック回帰の損失 | 交差エントロピー（ソフトマックス） | `LogMulticlass`（多クラスのロジスティック損失） |
| ロジスティック回帰の最適化 | バッチ勾配降下法（学習率 1.0）、5000 回 | `LogisticRegressionTrainer()` は AdaGrad、ミニバッチの大きさ 1 の確率的勾配降下法、5 エポック、シード 12345 |
| ランダムフォレストで特徴量を選ぶ単位 | 木ごと（2 つ） | 分割ごと（`fractionFeaturesInSplit` で割合を指定。この章では 0.5） |
| 決定木を分割する最小の件数 | 1 件になるまで分ける | `minChildWeight` の既定値 5（重みの合計が 5 未満の節は分割しない） |

`RandomForestTrainer` は、内側の決定木の `fractionFeaturesInSplit` が 1 のままだと、コンストラクターで例外を投げます。分割ごとに特徴量を絞ることが、Tribuo のランダムフォレストの前提になっています。

## 10.9 リファクタリング

`./gradlew check` で detekt を実行すると、`select(*配列)` の 3 か所が指摘されました（パスは `apps/kotlin/` からの相対パスに直しています）。

```text
src/main/kotlin/chapter10/FeatureImportance.kt:54:56: In most cases using a spread operator causes a full copy of the array to be created before calling a method. This may result in a performance penalty. [SpreadOperator]
src/main/kotlin/chapter10/RandomForest.kt:53:70: In most cases using a spread operator causes a full copy of the array to be created before calling a method. This may result in a performance penalty. [SpreadOperator]
src/main/kotlin/chapter10/RandomForest.kt:61:66: In most cases using a spread operator causes a full copy of the array to be created before calling a method. This may result in a performance penalty. [SpreadOperator]
```

スプレッド演算子は、可変長引数に渡すために配列をまるごとコピーします。まず `select(columns)` と列名のリストをそのまま渡してみましたが、Kotlin DataFrame 0.15.0 の `select` には `List<String>` を受け取る版が無く、コンパイルエラーになりました。そこで、列を 1 つずつ取り出してデータフレームを組み直す拡張関数にしました。

```kotlin
fun AnyFrame.selectColumns(names: List<String>): AnyFrame = names.map { this[it] }.toDataFrame()
```

`this[it]` で列（`DataColumn`）を取り出し、列のリストに `toDataFrame()` を呼ぶと、その列だけのデータフレームになります。`select(*columns.toTypedArray())` の 3 か所を `selectColumns(columns)` に置き換え、テストが通ったまま detekt の指摘が無くなることを確かめました。

## 10.10 実データで突き合わせる

### 実データのテスト

`prepareIris` で前処理した iris で、自作のモデルと Tribuo の正解率を確かめます。値は、実装を実データで動かして得たものをテストで固定しました（Red を経ていません）。

```kotlin
class IrisModelsTest {
    private val csvFile = File(dataDir(), "iris.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ iris.csv が配置されていない（gulp data:setup）")
    }

    private fun irisSplit(): TrainTestSplit<String> = prepareIris(csvFile, testSize = 0.3, seed = 0)

    @Test
    fun `ロジスティック回帰はテストデータの45件中42件を正しく分類する`() {
        val score = evaluate(LogisticRegression(), irisSplit())

        assertEquals(42.0 / 45, score.test, absoluteTolerance = 1e-12)
    }

    @Test
    fun `ランダムフォレストは訓練データを分け切りテストデータの45件中41件を正しく分類する`() {
        val score = evaluate(RandomForest(nEstimators = 100, maxFeatures = 2, seed = 0), irisSplit())

        assertEquals(1.0, score.train)
        assertEquals(41.0 / 45, score.test, absoluteTolerance = 1e-12)
    }

    @Test
    fun `Tribuoの既定のロジスティック回帰は5エポックでテストデータの45件中41件を正しく分類する`() {
        val score = evaluate(TribuoClassifier(LogisticRegressionTrainer()), irisSplit())

        assertEquals(84.0 / 105, score.train, absoluteTolerance = 1e-12)
        assertEquals(41.0 / 45, score.test, absoluteTolerance = 1e-12)
    }

    @Test
    fun `Tribuoのロジスティック回帰は500エポックで自作と同じ正解率になる`() {
        val trainer = LinearSGDTrainer(LogMulticlass(), AdaGrad(1.0, 0.1), 500, Trainer.DEFAULT_SEED)

        val tribuo = evaluate(TribuoClassifier(trainer), irisSplit())

        assertEquals(evaluate(LogisticRegression(), irisSplit()), tribuo)
    }

    @Test
    fun `Tribuoのランダムフォレストはテストデータの45件中41件を正しく分類する`() {
        val score = evaluate(tribuoRandomForest(nEstimators = 100, maxDepth = null, seed = 0L), irisSplit())

        assertEquals(41.0 / 45, score.test, absoluteTolerance = 1e-12)
    }

    @Test
    fun `Tribuoのランダムフォレストは最小の重みを1にすると訓練データを分け切る`() {
        val tree = CARTClassificationTrainer(Int.MAX_VALUE, 1.0f, 0.0f, 0.5f, GiniIndex(), 0L)
        val forest = TribuoClassifier(RandomForestTrainer(tree, VotingCombiner(), 100, 0L))

        val score = evaluate(forest, irisSplit())

        assertEquals(1.0, score.train)
        assertEquals(43.0 / 45, score.test, absoluteTolerance = 1e-12)
    }
}
```

`Tribuoのロジスティック回帰は500エポックで自作と同じ正解率になる` は、`Score` が data class なので、訓練データとテストデータの正解率の組をまとめて `assertEquals` で比べています。`LogisticRegressionTrainer()` は、`LinearSGDTrainer(LogMulticlass(), AdaGrad(1.0, 0.1), 5, Trainer.DEFAULT_SEED)` と同じ設定なので、エポック数だけを変えて比べられます。

### モデルを比べる

`./gradlew runChapter -Pchapter=10` で、iris のテストデータでの正解率と、ランダムフォレストの特徴量の重要度を表示します。表示のテストを先に書き、`main` が無いことを確かめてから実装しました。

```kotlin
    @Test
    fun `実行するとモデルごとの正解率とランダムフォレストの重要度を表示する`() {
        val output = captureStdout { main() }

        assertEquals(
            "モデル\t訓練データ\tテストデータ\n" +
                "決定木（深さ 2）\t0.9429\t0.9333\n" +
                "ロジスティック回帰\t0.9429\t0.9333\n" +
                "ランダムフォレスト（100 本）\t1.0000\t0.9111\n" +
                "ランダムフォレスト（100 本・深さ 2）\t0.9429\t0.9333\n" +
                "Tribuo ロジスティック回帰\t0.8000\t0.9111\n" +
                "Tribuo ランダムフォレスト（100 本）\t0.9810\t0.9111\n" +
                "\n" +
                "ランダムフォレスト（100 本）の特徴量の重要度:\n" +
                "がく片長さ\t0.2116\n" +
                "がく片幅\t0.1215\n" +
                "花弁長さ\t0.2695\n" +
                "花弁幅\t0.3973\n",
            output,
        )
    }
```

```text
> Task :compileTestKotlin FAILED
e: .../src/test/kotlin/chapter10/IrisModelsTest.kt:69:38 Unresolved reference 'main'.
```

```kotlin
// src/main/kotlin/chapter10/Main.kt
package chapter10

import chapter02.prepareIris
import dataset.dataDir
import org.tribuo.classification.sgd.linear.LogisticRegressionTrainer
import java.io.File
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

private const val TEST_SIZE = 0.3
private const val SEED = 0
private const val N_ESTIMATORS = 100
private const val MAX_FEATURES = 2
private const val SHALLOW_DEPTH = 2

private fun format(value: Double): String = "%.4f".format(Locale.ROOT, value)

fun models(): List<Pair<String, Classifier>> =
    listOf(
        "決定木（深さ $SHALLOW_DEPTH）" to DecisionTreeClassifier(maxDepth = SHALLOW_DEPTH),
        "ロジスティック回帰" to LogisticRegression(),
        "ランダムフォレスト（$N_ESTIMATORS 本）" to RandomForest(N_ESTIMATORS, MAX_FEATURES, seed = SEED),
        "ランダムフォレスト（$N_ESTIMATORS 本・深さ $SHALLOW_DEPTH）" to RandomForest(N_ESTIMATORS, MAX_FEATURES, SHALLOW_DEPTH, SEED),
        "Tribuo ロジスティック回帰" to TribuoClassifier(LogisticRegressionTrainer()),
        "Tribuo ランダムフォレスト（$N_ESTIMATORS 本）" to tribuoRandomForest(N_ESTIMATORS, maxDepth = null, seed = SEED.toLong()),
    )

fun main() {
    // Tribuo が学習の経過を標準エラーに出すので、警告以上だけにする
    Logger.getLogger("org.tribuo").level = Level.WARNING
    val split = prepareIris(File(dataDir(), "iris.csv"), testSize = TEST_SIZE, seed = SEED)
    println("モデル\t訓練データ\tテストデータ")
    for ((name, model) in models()) {
        val score = evaluate(model, split)
        println("$name\t${format(score.train)}\t${format(score.test)}")
    }

    val forest = RandomForest(N_ESTIMATORS, MAX_FEATURES, seed = SEED).fit(split.xTrain, split.tTrain)
    println("\nランダムフォレスト（$N_ESTIMATORS 本）の特徴量の重要度:")
    for ((feature, value) in forestImportances(forest, split.xTrain, split.tTrain)) {
        println("$feature\t${format(value)}")
    }
}
```

- `models()` の戻り値の型が `List<Pair<String, Classifier>>` なので、`for ((name, model) in models())` のループでは、自作のモデルか Tribuo かを気にせず `evaluate` を呼べます
- Tribuo は `java.util.logging` で学習の経過（「Building model 0」など）を標準エラーに出します。表示が読みにくくなるので、`org.tribuo` のロガーを警告以上だけに絞っています

```bash
./gradlew runChapter -Pchapter=10
```

```text
モデル	訓練データ	テストデータ
決定木（深さ 2）	0.9429	0.9333
ロジスティック回帰	0.9429	0.9333
ランダムフォレスト（100 本）	1.0000	0.9111
ランダムフォレスト（100 本・深さ 2）	0.9429	0.9333
Tribuo ロジスティック回帰	0.8000	0.9111
Tribuo ランダムフォレスト（100 本）	0.9810	0.9111

ランダムフォレスト（100 本）の特徴量の重要度:
がく片長さ	0.2116
がく片幅	0.1215
花弁長さ	0.2695
花弁幅	0.3973
```

結果から読み取れることは次のとおりです。

- **アンサンブルがいつも勝つわけではない**。深さを制限しないランダムフォレスト（0.9111）は、第 3 章の深さ 2 の決定木（0.9333）より低くなりました。木 1 本 1 本が訓練データを分け切るまで深く育ち（訓練データの正解率 1.0000）、iris のような小さなデータでは多数決でも過学習を打ち消し切れていません。木の深さを 2 に制限した森は、決定木と同じ 0.9333 です
- **テストデータ 45 件では、1 件の違いが 0.0222 の差になる**。1 回の分割で測った正解率の小さな差でモデルの優劣を決めるのは危険です。分け方を変えて何度も測る交差検証は第 11 章で扱います
- **ランダムフォレストの重要度は、決定木より複数の特徴量に分散する**。木ごとに使える特徴量を 2 つに絞っているので、花弁幅を使えない木は別の特徴量で分割するためです。深さ 3 の決定木では、花弁幅の重要度は 0.9348 でした（10.11 節の Notebook）

### Tribuo と突き合わせる

**ロジスティック回帰**: Tribuo の既定（5 エポック）は、訓練データの正解率 0.8000 がテストデータの 0.9111 より低く、学習が足りていない状態です。`LinearSGDTrainer` でエポック数だけを変えて実測しました。

| エポック数 | 訓練データ | テストデータ |
|-----------|-----------|-------------|
| 5（既定） | 0.8000 | 0.9111 |
| 50 | 0.9048 | 0.9333 |
| 500 | 0.9429 | 0.9333 |
| 5000 | 0.9524 | 0.9333 |

500 エポックで、自作（5000 回）と同じ訓練 0.9429・テスト 0.9333 になりました。この組み合わせはテストで固定しています。Tribuo はミニバッチの大きさ 1 で更新するので、500 エポックでは 105 件 × 500 = 52500 回、重みを更新します。自作は全件の誤差をまとめて 1 回に 1 度しか更新しないので、回数の数え方が違います。最適化の方法が違うので、正解率がそろっても重みの値は一致しません。

自作のほうも繰り返し回数を変えて確かめました。1000 回と 5000 回では訓練 0.9429・テスト 0.9333 で変わらず、20000 回では訓練 0.9524・テスト 0.9333 でした。テストデータの正解率は変わらないので、既定の繰り返し回数は Python 版と同じ 5000 回のままにしています。

**ランダムフォレスト**: 自作も Tribuo もテストデータは 0.9111（41/45）でしたが、Tribuo は訓練データで 0.9810 と分け切っていません。10.8 節の表の `minChildWeight`（既定値 5）が原因ではないかと考え、1.0 にして確かめると、訓練データは 1.0000、テストデータは 0.9556（43/45）になりました。重みの合計が 5 未満の節を分割しないという既定の設定が、1 本 1 本の木の深さを抑えていたのです。

同じ「ランダムフォレスト」という名前でも、特徴量を選ぶ単位（木ごとか分割ごとか）や、分割を止める条件が違えば、正解率は変わります。ライブラリの結果と比べるときは、名前ではなく設定をそろえて比べます。

テストの実行結果です。

```bash
./gradlew test --tests "chapter10.*"
```

第 10 章のテストは 31 件すべて通ります。データが無い環境では、実データのテスト 7 件がスキップされ、残りの 24 件が通ります。

## 10.11 Notebook で探索する

Notebook は `apps/kotlin/notebooks/chapter10_ensemble_exploration.ipynb` にあります。Tribuo の決定木とロジスティック回帰のモジュールを、JAR とは別のセルで読み込みます（第 3 章と同じ理由です）。

```kotlin
%use dataframe(0.15.0), kandy(0.8.0)
@file:DependsOn("org.tribuo:tribuo-classification-tree:4.3.2")
@file:DependsOn("org.tribuo:tribuo-classification-sgd:4.3.2")
```

```kotlin
@file:DependsOn("../build/libs/getting-started-ml.jar")
```

### モデルごとの正解率

```kotlin
val scores = models().map { (name, model) -> name to evaluate(model, split) }
dataFrameOf(
    "モデル" to scores.map { it.first },
    "訓練データ" to scores.map { it.second.train },
    "テストデータ" to scores.map { it.second.test },
)
```

`main` と同じ `models()` を使うので、表の値は 10.10 節の表示と同じです。

### ロジスティック回帰の損失の推移

```kotlin
val logistic = LogisticRegression().fit(split.xTrain, split.tTrain)
val losses =
    dataFrameOf(
        "繰り返し回数" to logistic.losses.indices.map { it + 1 },
        "交差エントロピー" to logistic.losses,
    )
losses.plot {
    line {
        x("繰り返し回数")
        y("交差エントロピー")
    }
    layout.title = "勾配降下法の繰り返し回数と損失"
}
```

```kotlin
val checkpoints = listOf(1, 10, 100, 1000, 5000)
dataFrameOf(
    "繰り返し回数" to checkpoints,
    "交差エントロピー" to checkpoints.map { logistic.losses[it - 1] },
)
```

表は、Notebook の出力を小数第 4 位に丸めたものです。

| 繰り返し回数 | 交差エントロピー |
|-------------|----------------|
| 1 | 1.0986 |
| 10 | 0.8207 |
| 100 | 0.4062 |
| 1000 | 0.2148 |
| 5000 | 0.1828 |

最初の損失 1.0986 は、3 品種に均等な確率（1/3）を出したときの交差エントロピー（ln 3）です。重みがすべて 0 から始まるので、最初はどの品種にも同じ確率を出します。折れ線グラフでは、最初の 100 回で急に下がり、その後はゆっくり下がり続けます。

```kotlin
val weightColumns =
    listOf("特徴量" to split.xTrain.columnNames()) +
        logistic.classes.mapIndexed { k, label -> label to logistic.weights.map { it[k] } }
dataFrameOf(*weightColumns.toTypedArray())
```

`weights[特徴量][品種]` から、品種ごとの列を組み立てています。表は小数第 2 位に丸めたものです。

| 特徴量 | Iris-setosa | Iris-versicolor | Iris-virginica |
|--------|------------|-----------------|----------------|
| がく片長さ | -5.88 | 1.76 | 4.12 |
| がく片幅 | 9.17 | -3.84 | -5.32 |
| 花弁長さ | -4.93 | 0.25 | 4.68 |
| 花弁幅 | -13.47 | -1.78 | 15.24 |

重みの表は、決定木のルールと同じく「モデルが何を手がかりにしたか」を読むのに使えます。花弁幅の重みは setosa で大きく負、virginica で大きく正です。花弁幅が大きいほど virginica のスコアが上がり setosa のスコアが下がる、という第 3 章の決定木と同じ傾向を、ロジスティック回帰は連続的な重みとして学習しています。

### モデル別の特徴量の重要度

```kotlin
val tree = checkNotNull(DecisionTree(3).fit(split.xTrain, split.tTrain).tree)
val forest = RandomForest(nEstimators = 100, maxFeatures = 2, seed = 0).fit(split.xTrain, split.tTrain)
val importances =
    mapOf(
        "決定木（深さ 3）" to treeImportances(tree, split.xTrain, split.tTrain),
        "ランダムフォレスト（100 本）" to forestImportances(forest, split.xTrain, split.tTrain),
    )
val importanceTable =
    dataFrameOf(
        "モデル" to importances.flatMap { (model, values) -> values.keys.map { model } },
        "特徴量" to importances.flatMap { (_, values) -> values.keys },
        "重要度" to importances.flatMap { (_, values) -> values.values },
    )
importanceTable.plot {
    bars {
        x("特徴量")
        y("重要度")
        fillColor("モデル")
    }
    layout.title = "モデル別の特徴量の重要度"
}
```

Kandy の棒グラフで 2 つのモデルを色分けするため、「モデル・特徴量・重要度」の 3 列の縦長のデータにしています。値を小数第 4 位に丸めて横に並べると、次のとおりです。

| 特徴量 | 決定木（深さ 3） | ランダムフォレスト（100 本） |
|--------|----------------|--------------------------|
| がく片長さ | 0.0505 | 0.2116 |
| がく片幅 | 0.0000 | 0.1215 |
| 花弁長さ | 0.0147 | 0.2695 |
| 花弁幅 | 0.9348 | 0.3973 |

2 つのモデルとも花弁幅が最も重要という点は共通しています。決定木は花弁幅に 9 割以上が集中するのに対し、ランダムフォレストは他の特徴量にも分散します。

### 森の大きさと正解率

```kotlin
val sizes = listOf(1, 5, 10, 25, 50, 100)
val sizeScores =
    sizes.map { n ->
        Triple(
            n,
            evaluate(RandomForest(nEstimators = n, maxFeatures = 2, seed = 0), split),
            evaluate(tribuoRandomForest(nEstimators = n, maxDepth = null, seed = 0L), split),
        )
    }
val sizeTable =
    dataFrameOf(
        "木の数" to sizeScores.map { it.first },
        "自作（訓練データ）" to sizeScores.map { it.second.train },
        "自作（テストデータ）" to sizeScores.map { it.second.test },
        "Tribuo（テストデータ）" to sizeScores.map { it.third.test },
    )
sizeTable
```

表は、Notebook の出力を小数第 4 位に丸めたものです。Notebook では、この表を縦長のデータにして折れ線グラフも描いています。

| 木の数 | 自作（訓練データ） | 自作（テストデータ） | Tribuo（テストデータ） |
|-------|-----------------|------------------|--------------------|
| 1 | 0.8571 | 0.6222 | 0.9111 |
| 5 | 0.9905 | 0.8222 | 0.9333 |
| 10 | 0.9905 | 0.8667 | 0.9333 |
| 25 | 1.0000 | 0.8667 | 0.9333 |
| 50 | 1.0000 | 0.9111 | 0.9111 |
| 100 | 1.0000 | 0.9111 | 0.9111 |

自作の森は、木が 1 本のときテストデータの正解率が 0.6222 にとどまり、木を増やすと上がって 50 本以降は横ばいです。自作の木は 1 本の中で使える特徴量が 2 つに固定されるので、花弁幅を含まない組み合わせを引いた木は弱くなります。一方、Tribuo の森は木が 1 本でも 0.9111 です。Tribuo は分割のたびに特徴量を選び直すので、1 本の木の中でも花弁幅を使う機会があるためだと考えられます。`evaluate` を使い回しているので、ループで条件を変えるだけで自作と Tribuo を比べられます。

グラフの画像は、第 2 章と同じ理由で記事に載せていません。学習データを配置して、手元の Notebook で確認してください。

<details>
<summary>この章の完成コード（src/main/kotlin/chapter10/LogisticRegression.kt）</summary>

```kotlin
package chapter10

import org.jetbrains.kotlinx.dataframe.AnyFrame
import kotlin.math.exp
import kotlin.math.ln

fun softmax(z: DoubleArray): DoubleArray {
    val max = z.max()
    val exps = z.map { exp(it - max) }
    val total = exps.sum()
    return exps.map { it / total }.toDoubleArray()
}

private const val EPSILON = 1e-12

fun crossEntropy(
    probabilities: List<DoubleArray>,
    targets: List<Int>,
): Double = -probabilities.indices.sumOf { i -> ln(probabilities[i][targets[i]] + EPSILON) } / probabilities.size

fun AnyFrame.toRows(): List<DoubleArray> {
    val columns = columnNames().map { name -> this[name].values().map { (it as Number).toDouble() } }
    return List(rowsCount()) { row -> DoubleArray(columns.size) { columns[it][row] } }
}

class LogisticRegression(
    private val learningRate: Double = 1.0,
    private val epochs: Int = 5000,
) : Classifier {
    var classes: List<String> = emptyList()
        private set

    // weights[特徴量][品種]
    var weights: Array<DoubleArray> = emptyArray()
        private set

    var bias: DoubleArray = DoubleArray(0)
        private set

    var losses: List<Double> = emptyList()
        private set

    private fun scores(row: DoubleArray): DoubleArray =
        DoubleArray(classes.size) { k -> row.indices.sumOf { f -> row[f] * weights[f][k] } + bias[k] }

    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): LogisticRegression {
        val rows = x.toRows()
        classes = t.distinct().sorted()
        val targets = t.map { classes.indexOf(it) }
        weights = Array(x.columnsCount()) { DoubleArray(classes.size) }
        bias = DoubleArray(classes.size)
        val recorded = mutableListOf<Double>()
        repeat(epochs) {
            val probabilities = rows.map { softmax(scores(it)) }
            recorded += crossEntropy(probabilities, targets)
            // 確率 − 正解（正解の品種だけ 1 を引く）
            val errors = probabilities.mapIndexed { i, p -> p.copyOf().also { it[targets[i]] -= 1.0 } }
            for (k in classes.indices) {
                for (f in weights.indices) {
                    weights[f][k] -= learningRate * rows.indices.sumOf { i -> rows[i][f] * errors[i][k] } / rows.size
                }
                bias[k] -= learningRate * errors.sumOf { it[k] } / rows.size
            }
        }
        losses = recorded
        return this
    }

    override fun predict(x: AnyFrame): List<String> {
        check(classes.isNotEmpty()) { "fit で学習してから predict を呼んでください" }
        return x.toRows().map { row ->
            val scores = scores(row)
            classes[scores.indices.maxBy { scores[it] }]
        }
    }
}
```

</details>

<details>
<summary>この章の完成コード（src/main/kotlin/chapter10/RandomForest.kt）</summary>

ktlint で整形した後のコードです。本文のコードとは、長い式の折り返しが違います。

```kotlin
package chapter10

import chapter03.DecisionTree
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import kotlin.random.Random

fun majorityVote(votes: List<List<String>>): List<String> =
    votes.first().indices.map { sample ->
        votes
            .map { it[sample] }
            .groupingBy { it }
            .eachCount()
            .maxBy { it.value }
            .key
    }

fun bootstrapSample(
    size: Int,
    random: Random,
): List<Int> = List(size) { random.nextInt(size) }

fun AnyFrame.selectColumns(names: List<String>): AnyFrame = names.map { this[it] }.toDataFrame()

data class FittedTree(
    val columns: List<String>,
    val rows: List<Int>,
    val model: DecisionTree,
)

class RandomForest(
    private val nEstimators: Int = 10,
    private val maxFeatures: Int = 2,
    private val maxDepth: Int? = null,
    private val seed: Int = 0,
) : Classifier {
    var trees: List<FittedTree> = emptyList()
        private set

    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): RandomForest {
        val random = Random(seed)
        trees =
            List(nEstimators) {
                val rows = bootstrapSample(x.rowsCount(), random)
                val chosen =
                    x
                        .columnNames()
                        .shuffled(random)
                        .take(maxFeatures)
                        .toSet()
                val columns = x.columnNames().filter { it in chosen }
                val model = DecisionTree(maxDepth).fit(x[rows].selectColumns(columns), t.slice(rows))
                FittedTree(columns = columns, rows = rows, model = model)
            }
        return this
    }

    override fun predict(x: AnyFrame): List<String> {
        check(trees.isNotEmpty()) { "fit で学習してから predict を呼んでください" }
        return majorityVote(trees.map { it.model.predict(x.selectColumns(it.columns)) })
    }
}
```

</details>

<details>
<summary>この章の完成コード（src/main/kotlin/chapter10/FeatureImportance.kt）</summary>

```kotlin
package chapter10

import chapter03.Leaf
import chapter03.Node
import chapter03.Tree
import chapter03.gini
import org.jetbrains.kotlinx.dataframe.AnyFrame

private fun impurityDecreases(
    tree: Tree,
    x: AnyFrame,
    t: List<String>,
): List<Pair<String, Double>> =
    when (tree) {
        is Leaf -> {
            emptyList()
        }

        is Node -> {
            val split = tree.split
            val goesLeft = x[split.feature].values().map { (it as Number).toDouble() <= split.threshold }
            val left = goesLeft.indices.filter { goesLeft[it] }
            val right = goesLeft.indices.filterNot { goesLeft[it] }
            listOf(split.feature to t.size * (gini(t) - split.impurity)) +
                impurityDecreases(tree.left, x[left], t.slice(left)) +
                impurityDecreases(tree.right, x[right], t.slice(right))
        }
    }

fun treeImportances(
    tree: Tree,
    x: AnyFrame,
    t: List<String>,
): Map<String, Double> {
    val decreases = impurityDecreases(tree, x, t)
    val totals = x.columnNames().associateWith { feature -> decreases.filter { it.first == feature }.sumOf { it.second } }
    return normalize(totals)
}

private fun normalize(totals: Map<String, Double>): Map<String, Double> {
    val total = totals.values.sum()
    return if (total == 0.0) totals else totals.mapValues { it.value / total }
}

fun forestImportances(
    forest: RandomForest,
    x: AnyFrame,
    t: List<String>,
): Map<String, Double> {
    val perTree =
        forest.trees.map { fitted ->
            val tree = checkNotNull(fitted.model.tree)
            treeImportances(tree, x[fitted.rows].selectColumns(fitted.columns), t.slice(fitted.rows))
        }
    val totals = x.columnNames().associateWith { feature -> perTree.sumOf { it[feature] ?: 0.0 } / forest.trees.size }
    return normalize(totals)
}
```

</details>

<details>
<summary>この章の完成コード（src/main/kotlin/chapter10/Classifier.kt）</summary>

```kotlin
package chapter10

import chapter01.accuracy
import chapter02.TrainTestSplit
import chapter03.DecisionTree
import org.jetbrains.kotlinx.dataframe.AnyFrame

interface Classifier {
    fun fit(
        x: AnyFrame,
        t: List<String>,
    ): Classifier

    fun predict(x: AnyFrame): List<String>
}

class DecisionTreeClassifier(
    maxDepth: Int? = null,
) : Classifier {
    private val tree = DecisionTree(maxDepth)

    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): DecisionTreeClassifier {
        tree.fit(x, t)
        return this
    }

    override fun predict(x: AnyFrame): List<String> = tree.predict(x)
}

data class Score(
    val train: Double,
    val test: Double,
)

fun evaluate(
    model: Classifier,
    split: TrainTestSplit<String>,
): Score {
    model.fit(split.xTrain, split.tTrain)
    return Score(
        train = accuracy(model.predict(split.xTrain), split.tTrain),
        test = accuracy(model.predict(split.xTest), split.tTest),
    )
}
```

</details>

<details>
<summary>この章の完成コード（src/main/kotlin/chapter10/TribuoClassifier.kt）</summary>

```kotlin
package chapter10

import chapter03.predictWithTribuo
import chapter03.toTribuoDataset
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.tribuo.Model
import org.tribuo.Trainer
import org.tribuo.classification.Label
import org.tribuo.classification.dtree.CARTClassificationTrainer
import org.tribuo.classification.ensemble.VotingCombiner
import org.tribuo.common.tree.RandomForestTrainer

class TribuoClassifier(
    private val trainer: Trainer<Label>,
) : Classifier {
    private var model: Model<Label>? = null

    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): TribuoClassifier {
        model = trainer.train(toTribuoDataset(x, t))
        return this
    }

    override fun predict(x: AnyFrame): List<String> {
        val fitted = checkNotNull(model) { "fit で学習してから predict を呼んでください" }
        return predictWithTribuo(fitted, x)
    }
}

// 分割ごとに半分の特徴量から選ぶ決定木を、ブートストラップ標本で nEstimators 本学習して多数決する
private const val FRACTION_FEATURES_IN_SPLIT = 0.5f

fun tribuoRandomForest(
    nEstimators: Int,
    maxDepth: Int?,
    seed: Long,
): TribuoClassifier {
    val tree = CARTClassificationTrainer(maxDepth ?: Int.MAX_VALUE, FRACTION_FEATURES_IN_SPLIT, seed)
    return TribuoClassifier(RandomForestTrainer(tree, VotingCombiner(), nEstimators, seed))
}
```

</details>

## 10.12 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、共通のインターフェースで Tribuo と並べて評価しました。

1. **数値として正しい実装** — ソフトマックス関数は、定義どおりでは大きな値で `NaN` になった。JVM は警告を出さないので、境界の値のテストで見つけ、最大値を引く方法で直した
2. **配列と添字による学習** — NumPy の行列計算の代わりに、`DoubleArray` と `sumOf` でバッチ勾配降下法を書いた。正解の品種の確率から 1 を引くことで、one-hot 表現を作らずに誤差を求めた
3. **部品の再利用** — 第 3 章の決定木を変更せずに組み合わせ、ブートストラップ標本と特徴量の部分集合でランダムフォレストを作った。1 本分の情報は data class にまとめた
4. **名前的部分型とアダプター** — Kotlin の `interface` は実装の宣言が必要なので、第 3 章の決定木と Tribuo のトレーナーをアダプターで包み、自作のモデルと同じ `evaluate` で評価した
5. **設定をそろえて突き合わせる** — Tribuo のロジスティック回帰はエポック数、ランダムフォレストは `minChildWeight` と特徴量を選ぶ単位が自作と違った。設定を変えて実測し、違いの理由をテストに残した

iris のテストデータ 45 件では、どのモデルも数件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。
