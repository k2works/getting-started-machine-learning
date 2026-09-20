---
type: Article
title: "第 10 章: ロジスティック回帰とアンサンブル学習"
description: "ソフトマックスと勾配降下法のロジスティック回帰、第 3 章の決定木を再利用したランダムフォレストと特徴量の重要度を Scala の TDD で自作し、trait Classifier とアダプターで共通化して Java 版と数値を突き合わせる。"
tags: [article,getting-start-ml,scala]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T03:30:00Z }
---

# 第 10 章: ロジスティック回帰とアンサンブル学習

## 10.1 はじめに

第 3 章では、決定木を自作して iris を分類しました。この章では、分類のアルゴリズムを 2 つ増やします。

- **ロジスティック回帰**: 特徴量の重み付きの和から、各品種である確率を求めるモデル。勾配降下法で重みを学習する
- **ランダムフォレスト**: 少しずつ違うデータで学習した決定木をたくさん作り、多数決で予測するモデル。複数のモデルを組み合わせる手法を **アンサンブル学習** と呼ぶ

モデルが増えると、「学習させて正解率を測る」処理をモデルごとに書くのは重複です。そこで、`trait Classifier` でモデルに共通する操作（`fit` と `predict`）を表し、どのモデルも同じ関数で評価できるようにします。最後に、どの特徴量が分類に効いたかを表す **特徴量の重要度** を自作します。

[Python 版の第 10 章](../python/10-logistic-regression-and-ensemble.md) と同じ TODO リストで進め、[Java 版](../java/10-logistic-regression-and-ensemble.md)・[Kotlin 版](../kotlin/10-logistic-regression-and-ensemble.md)（同じ JVM）と [F# 版](../fsharp/10-logistic-regression-and-ensemble.md)（関数型）を対比しながら Scala の書き方を見ていきます。注目してほしいのは次の 3 点です。

- Scala の `trait` も Java・Kotlin の `interface` と同じく **継承を宣言した型だけ** を受け入れます。第 3 章の `DecisionTree` を変更せずに共通の型に合わせるため、アダプターを書きます。F# 版が分類器を「関数の型」で表したのとの違いがはっきり出るところです
- 勾配降下法の重みの更新を、Java 版の三重の `for` 文ではなく `Vector` の `map`・`zip`・`foldLeft` で書きます。`var` に入れ替える不変のベクトルとして重みを持ちます
- 乱数は Kotlin 版の `kotlin.random.Random` ではなく `java.util.Random` を使い、列の並べ替えも Java の `Collections.shuffle` と同じ手順を自分で書きます。分割（第 2 章）も Java 版と同じなので、**この章の数値は Java 版と完全に一致します**

Tribuo との突き合わせは、この章の Scala 版には含めません（10.8 節）。データは第 2 章・第 3 章と同じ iris を使い、第 2 章の `Preprocessing.prepareIris` で前処理します。

## 10.2 TODO リストの作成

**TODO リスト**:

- [ ] ソフトマックス関数で確率に変換する
  - [ ] 合計が 1 になる確率にする
  - [ ] 大きな値でもあふれない
- [ ] 交差エントロピーで損失を測る
- [ ] ロジスティック回帰を学習して予測する
  - [ ] 分けられるデータを正しく予測する
  - [ ] 学習した品種が名前の順に並ぶ
  - [ ] 繰り返すほど損失が小さくなる
  - [ ] 学習する前に予測するとエラーになる
- [ ] ランダムフォレストを学習して予測する
  - [ ] 多数決で予測を 1 つに決める
  - [ ] ブートストラップ標本を選ぶ
  - [ ] 第 3 章の決定木を再利用する
  - [ ] 同じシードなら同じ森になる
- [ ] 特徴量の重要度を計算する
  - [ ] 決定木 1 本の重要度
  - [ ] ランダムフォレストの重要度
- [ ] どのモデルも同じ約束で評価する
  - [ ] 第 3 章の決定木を変更せずに共通の型に合わせる
- [ ] 実データでモデルを比べ、Java 版と突き合わせる

## 10.3 ソフトマックス関数

### ロジスティック回帰の仕組み

ロジスティック回帰は、品種ごとに「特徴量の重み付きの和 + 切片」を計算し、それを **ソフトマックス関数** で確率に変換します。

```text
スコア(品種 k) = w(k, がく片長さ) × がく片長さ + … + w(k, 花弁幅) × 花弁幅 + b(k)
確率(品種 k)   = exp(スコア(品種 k)) / Σ exp(スコア(品種 j))
```

多クラスへの広げ方には、品種ごとに「その品種か、それ以外か」の 2 値分類器を作る one-vs-rest と、この章のように 1 つのモデルで全品種の確率を同時に求めるソフトマックス（多項ロジスティック回帰）があります。ソフトマックスを選んだのは、3 品種の確率の合計が必ず 1 になり、確率として解釈しやすいからです。

### 仮実装

**1 サンプル分のスコア**（`Vector[Double]`）を受け取る関数にします。Scala にはコンパニオンオブジェクトがあるので、Java 版の static メソッド・Kotlin 版のトップレベル関数に当たるものは `object LogisticRegression` に置きます。テストは第 1 章・第 3 章と同じ ScalaTest の `AnyFunSuite` です。

```scala
// src/test/scala/machinelearning/chapter10/LogisticRegressionSpec.scala
package machinelearning.chapter10

import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class LogisticRegressionSpec extends AnyFunSuite:
  test("ソフトマックスは合計が 1 になる確率にする") {
    val probabilities = LogisticRegression.softmax(Vector(1.0, 2.0, 3.0))

    assert(probabilities.sum === 1.0 +- 1e-12)
    assert(probabilities(2) > probabilities(1) && probabilities(1) > probabilities(0))
  }
```

最初の Red は、第 1 章と同じくコンパイラからの `E006` です。

```text
[error] -- [E006] Not Found Error: .../src/test/scala/machinelearning/chapter10/LogisticRegressionSpec.scala:8:24
[error] 8 |    val probabilities = LogisticRegression.softmax(Vector(1.0, 2.0, 3.0))
[error]   |                        ^^^^^^^^^^^^^^^^^^
[error]   |                        Not found: LogisticRegression
[error]   |
[error]   | longer explanation available when compiling with `-explain`
[error] one error found
```

均等な確率を返す仮実装を置きます。

```scala
// src/main/scala/machinelearning/chapter10/LogisticRegression.scala
package machinelearning.chapter10

/** ソフトマックスと勾配降下法によるロジスティック回帰。 */
object LogisticRegression:
  /** スコアを、合計が 1 になる確率に変換する。 */
  def softmax(z: Vector[Double]): Vector[Double] = Vector.fill(z.size)(1.0 / z.size)
```

この仮実装は「合計が 1」は満たしますが、「大きいスコアほど確率が高い」は満たしません。1 つのテストに 2 つのアサーションを置いたので、2 つめで落ちます。

```text
[info] LogisticRegressionSpec:
[info] - ソフトマックスは合計が 1 になる確率にする *** FAILED ***
[info]   0.3333333333333333 was not greater than 0.3333333333333333 (LogisticRegressionSpec.scala:11)
```

ScalaTest の `assert` はマクロで、失敗したときに式の左右の値をそのまま示します。Java 版の AssertJ のように `isCloseTo`・`containsExactly` を選ばなくても、素の `assert` と `===`・`+-` だけで失敗の理由が読めます。

定義どおりに一般化します。

```scala
  def softmax(z: Vector[Double]): Vector[Double] =
    val exps = z.map(math.exp)
    val total = exps.sum
    exps.map(_ / total)
```

`z.map(math.exp)` は、Java 版の `Arrays.stream(z).map(Math::exp).toArray()` に当たります。`Vector` はそのまま `map`・`sum` を持つので、ストリームへの出し入れが要りません。

### 大きな値でもあふれない

学習の途中では、スコアが大きな値になることがあります。

```scala
  test("大きな値でもあふれない") {
    val probabilities = LogisticRegression.softmax(Vector(1000.0, 1001.0))

    assert(probabilities.forall(p => !p.isNaN))
    assert(probabilities.sum === 1.0 +- 1e-12)
  }
```

```text
[info] LogisticRegressionSpec:
[info] - ソフトマックスは合計が 1 になる確率にする
[info] - 大きな値でもあふれない *** FAILED ***
[info]   probabilities.forall(((p: scala.Double) => scala.Predef.double2Double(p).isNaN().unary_!)) was false (LogisticRegressionSpec.scala:17)
```

`math.exp(1000.0)` は `Double` で表せる範囲を超えて `Infinity` になり、`Infinity / Infinity` が `NaN`（非数）になりました。JVM の浮動小数点数の計算は警告も例外も出さないので、境界の値のテストが無ければ気づけません。Java 版・Kotlin 版でも同じところでつまずいています。

失敗メッセージに `scala.Predef.double2Double(p).isNaN().unary_!` と、脱糖された式がそのまま出ているのが Scala らしいところです。`!p.isNaN` の `!` が `unary_!` というメソッド呼び出しであること、`Double` から `java.lang.Double` への暗黙の変換が入っていることが読み取れます。

ソフトマックスは、すべての値から同じ数を引いても結果が変わりません（分子と分母に同じ `exp(-c)` が掛かるため）。そこで最大値を引いてから `exp` を計算します。

```scala
  /** スコアを、合計が 1 になる確率に変換する。最大値を引いてから exp を求めるので、大きな値でもあふれない。 */
  def softmax(z: Vector[Double]): Vector[Double] =
    val max = z.max
    val exps = z.map(v => math.exp(v - max))
    val total = exps.sum
    exps.map(_ / total)
```

Java 版は空の配列に備えて `OptionalDouble` を `orElseThrow()` で開きました。Scala の `Vector#max` は空なら `UnsupportedOperationException` を投げるだけなので、そのまま書けます。空のスコアを渡す呼び出しはこの章に無く、テストでも表していないので、ここでは検査を足していません。

```text
[info] LogisticRegressionSpec:
[info] - ソフトマックスは合計が 1 になる確率にする
[info] - 大きな値でもあふれない
```

## 10.4 ロジスティック回帰

### 交差エントロピー

学習の進み具合は **交差エントロピー**（正解の品種の確率の対数の平均に、マイナスを付けたもの）で測ります。正解の確率が高いほど小さくなる、という性質をテストにします。

```scala
  test("正解の確率が高いほど交差エントロピーは小さい") {
    val confident = LogisticRegression.crossEntropy(Vector(Vector(0.9, 0.1)), Vector(0))
    val unsure = LogisticRegression.crossEntropy(Vector(Vector(0.5, 0.5)), Vector(0))

    assert(confident < unsure)
  }
```

```scala
object LogisticRegression:
  private val Epsilon = 1e-12

  /** 交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。 */
  def crossEntropy(probabilities: Vector[Vector[Double]], targets: Vector[Int]): Double =
    -probabilities
      .zip(targets)
      .map((p, target) => math.log(p(target) + Epsilon))
      .sum / probabilities.size
```

確率と正解を添字でそろえる代わりに `zip` で組にしています。Java 版は `IntStream.range(0, size)` と添字で書いたところです。`map((p, target) => …)` は、タプルを 2 つの引数で受け取る Scala 3 の書き方で、`map { case (p, target) => … }` と書かずに済みます。

`+ Epsilon` は、確率が 0 になったときに `log(0) = -Infinity` にならないための下駄です。

### 学習と予測

第 3 章の決定木と同じく `fit` と `predict` を持つクラスにします。テスト用のデータは、第 3 章のテストに置いた `Samples`（1 列だけの特徴量を作るヘルパーと、3 品種 6 件の架空の値）をそのまま使います。テストのパッケージが違っても、テストのソースセットが同じなので `machinelearning.chapter03.Samples` として参照できます。

```scala
  test("分けられるデータを学習すると訓練データを正しく予測する") {
    val model = LogisticRegression(learningRate = 1.0, epochs = 500)
      .fit(Samples.threeSpeciesX, Samples.threeSpeciesT)

    assert(model.predict(Samples.threeSpeciesX) === Samples.threeSpeciesT)
  }

  test("学習した品種は名前の順に並ぶ") {
    val model = LogisticRegression(epochs = 10).fit(Samples.threeSpeciesX, Samples.threeSpeciesT)

    assert(model.learnedClasses === Vector("setosa", "versicolor", "virginica"))
  }

  test("繰り返すほど損失は小さくなる") {
    val model = LogisticRegression(epochs = 100).fit(Samples.threeSpeciesX, Samples.threeSpeciesT)

    assert(model.losses.size === 100)
    assert(model.losses.last < model.losses.head)
  }

  test("学習する前に予測するとエラーになる") {
    assertThrows[IllegalArgumentException](LogisticRegression().predict(Samples.threeSpeciesX))
  }
```

Java 版は、既定値を渡すコンストラクターと 2 引数のコンストラクターを並べ、テストでは `new LogisticRegression(1.0, 100)` と学習率も書く必要がありました。Scala には既定引数と名前付き引数があるので、`LogisticRegression(epochs = 100)` と変えたいものだけを書けます。Kotlin 版と同じ書き心地です。

```scala
// src/main/scala/machinelearning/chapter10/LogisticRegression.scala
/** ソフトマックスと勾配降下法によるロジスティック回帰。 */
class LogisticRegression(learningRate: Double = 1.0, epochs: Int = 5000) extends Classifier:
  private var classes: Vector[String] = Vector.empty
  // weights(特徴量)(品種)
  private var weights: Vector[Vector[Double]] = Vector.empty
  private var bias: Vector[Double] = Vector.empty
  private var recorded: Vector[Double] = Vector.empty

  /** 学習した品種の並び（名前の順）。 */
  def learnedClasses: Vector[String] = classes

  /** 繰り返しごとの訓練データの損失。 */
  def losses: Vector[Double] = recorded

  /** バッチ勾配降下法で重みと切片を学習する。 */
  override def fit(x: Vector[Features], t: Vector[String]): LogisticRegression =
    val rows = x.map(_.values)
    classes = t.distinct.sorted
    val targets = t.map(classes.indexOf)
    weights = Vector.fill(x.head.columns.size, classes.size)(0.0)
    bias = Vector.fill(classes.size)(0.0)
    recorded = (0 until epochs).toVector.map { _ =>
      val probabilities = rows.map(row => LogisticRegression.softmax(scores(row)))
      val loss = LogisticRegression.crossEntropy(probabilities, targets)
      // 確率 − 正解（正解の品種だけ 1 を引く）
      val errors = probabilities.zip(targets).map { (probability, target) =>
        probability.updated(target, probability(target) - 1.0)
      }
      update(rows, errors)
      loss
    }
    this

  override def predict(x: Vector[Features]): Vector[String] =
    require(classes.nonEmpty, "fit で学習してから predict を呼んでください")
    x.map(features => classes(argMax(LogisticRegression.softmax(scores(features.values)))))
```

- 第 2 章の `Features.values` は `Vector[Double]` を返します。`Vector` は不変なので、Java 版のように「配列の写しを返しているから `fit` の最初に 1 回だけ取り出す」といった配慮は要りません。それでも `fit` の頭で `rows` に取り出しているのは、繰り返しの中で `Features` から値を取り出す手間を省くためです
- `classes = t.distinct.sorted` で、品種を名前の順に並べます。Java 版の `t.stream().distinct().sorted().toList()` と同じです
- 誤差の計算では、Java 版が `clone()` した配列の要素を書き換えたところを、`probability.updated(target, probability(target) - 1.0)`（その位置だけ差し替えた新しい `Vector`）で書いています。正解の品種を 1、それ以外を 0 とした表（one-hot 表現）を作らず、**正解の品種の確率からだけ 1 を引く** のは Python 版・Kotlin 版と同じ手です
- 損失の記録は `(0 until epochs).toVector.map { … }` の戻り値です。Java 版は `ArrayList` に `add` して最後に `List.copyOf` で固めましたが、Scala では繰り返しの結果がそのまま不変の `Vector` になります。`losses` はその `Vector` を返すだけで、呼び出し側が書き換える余地がありません
- 学習前の予測は `require` で止めます。Java 版・Kotlin 版は第 3 章と同じ `IllegalStateException` にしましたが、Scala の `require` が投げるのは `IllegalArgumentException` です。第 3 章の `DecisionTree` は `getOrElse(throw IllegalStateException(…))` と書いているので、この章のモデルとは例外の型が揃っていません。メッセージは同じにしてあります

重みと切片の更新は、`Vector` の入れ替えで書きます。

```scala
  /** 特徴量ごとのスコア（切片 + 重み × 値）。 */
  private def scores(row: Vector[Double]): Vector[Double] =
    row.zip(weights).foldLeft(bias) { (acc, pair) =>
      val (value, weightsForFeature) = pair
      acc.zip(weightsForFeature).map((score, weight) => score + value * weight)
    }

  private def update(rows: Vector[Vector[Double]], errors: Vector[Vector[Double]]): Unit =
    val n = rows.size
    weights = weights.zipWithIndex.map { (weightsForFeature, f) =>
      weightsForFeature.zipWithIndex.map { (weight, k) =>
        val gradient = rows.zip(errors).map((row, error) => row(f) * error(k)).sum
        weight - learningRate * gradient / n
      }
    }
    bias = bias.zipWithIndex.map { (value, k) =>
      value - learningRate * errors.map(_(k)).sum / n
    }

  private def argMax(values: Vector[Double]): Int = values.zipWithIndex.maxBy(_._1)._2
```

- `scores` は、切片のベクトルを初期値にして、特徴量ごとに「値 × 重み」を足し込む `foldLeft` です。Java 版は `double[] scores = bias.clone()` から始めて二重の `for` 文で足しました。畳み込みで書くと、「切片から始めて、特徴量の数だけ足していく」という形が式に出ます
- `update` も、Java 版の三重の `for` 文（品種 × 特徴量 × サンプル）が、`map` の入れ子と `sum` の 3 段になります。1 回の繰り返しで全サンプルの誤差を先に求めてから重みを更新するので、Python 版・Java 版と同じ **バッチ勾配降下法** です
- 更新するたびに新しい `Vector` を作り、`var` の `weights`・`bias` に入れ替えます。「値は不変、入れ物だけ可変」という持ち方で、第 3 章の `DecisionTree` が学習結果を `var tree: Option[Tree]` に持つのと同じ形です
- `predict` は確率を計算してから最大の品種を選んでいます。ソフトマックスは大小関係を変えないので、スコアのまま選んでも結果は同じです。`argMax` は `zipWithIndex.maxBy(_._1)._2` の 1 行で、Java 版が書いた `for` 文が要りません

```text
[info] LogisticRegressionSpec:
[info] - ソフトマックスは合計が 1 になる確率にする
[info] - 大きな値でもあふれない
[info] - 正解の確率が高いほど交差エントロピーは小さい
[info] - 分けられるデータを学習すると訓練データを正しく予測する
[info] - 学習した品種は名前の順に並ぶ
[info] - 繰り返すほど損失は小さくなる
[info] - 学習する前に予測するとエラーになる
```

## 10.5 ランダムフォレスト

### 仕組み

ランダムフォレストは、次の 2 つの「ばらつき」を持たせた決定木をたくさん作り、予測を多数決で決めます。

1. **ブートストラップ標本**: 訓練データから、同じ件数を重複を許して選び直したデータで木を学習する（バギング）
2. **特徴量の部分集合**: 木ごとに使う特徴量を一部だけに絞る

1 本 1 本の決定木は訓練データの細部を覚えて過学習しがちですが、違うデータ・違う特徴量で学習した木の多数決を取ると、個々の木の癖が打ち消し合います。

この章では、第 3 章の `DecisionTree` を **変更せずに** 再利用します。そのため、特徴量の絞り込みは「木ごと」に行います。

### 多数決とブートストラップ標本

```scala
class RandomForestSpec extends AnyFunSuite:
  test("同数でなければ多数派の予測を選ぶ") {
    val votes = Vector(Vector("a", "b"), Vector("a", "b"), Vector("c", "a"))

    assert(RandomForest.majorityVote(votes) === Vector("a", "b"))
  }

  test("同数なら先に現れた予測を選ぶ") {
    assert(RandomForest.majorityVote(Vector(Vector("b"), Vector("a"))) === Vector("b"))
  }

  test("ブートストラップ標本は重複を許して同じ件数を選ぶ") {
    val sample = RandomForest.bootstrapSample(10, Random(0))

    assert(sample.size === 10)
    assert(sample.forall(i => i >= 0 && i < 10))
  }
```

`votes` は「木ごとの予測のベクトル」です。サンプルごとの多数決に組み替えます。

```scala
object RandomForest:
  /** サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。 */
  def majorityVote(votes: Vector[Vector[String]]): Vector[String] =
    votes.head.indices.toVector.map(sample => mostCommon(votes.map(_(sample))))

  /** 0 から size - 1 までの行番号を、重複を許して size 個選ぶ。 */
  def bootstrapSample(size: Int, random: Random): Vector[Int] =
    Vector.fill(size)(random.nextInt(size))

  private def mostCommon(labels: Vector[String]): String =
    val counts = labels.foldLeft(scala.collection.immutable.ListMap.empty[String, Int]) {
      (acc, label) => acc.updated(label, acc.getOrElse(label, 0) + 1)
    }
    counts.maxBy(_._2)._1
```

件数は第 3 章の `DecisionTrees.majority` と同じく、`ListMap` への `foldLeft` で数えます。`ListMap` は入れた順を保ち、`maxBy` は最初の最大値を返すので、同数なら先に現れた予測が選ばれます。Java 版の `LinkedHashMap` + `merge` に当たる書き方です。

`bootstrapSample` が乱数生成器を引数で受け取るのは、森全体で 1 つの生成器を使い回し、シード 1 つで全部の木の乱数を再現できるようにするためです。`java.util.Random` を使うので、**選ばれる行は Java 版と完全に一致します**（Kotlin 版は `kotlin.random.Random` なので一致しません）。

### 森を作る

```scala
  test("同じシードなら同じ森になる") {
    val first = RandomForest.of(5, 1, 0).fit(Samples.threeSpeciesX, Samples.threeSpeciesT)
    val second = RandomForest.of(5, 1, 0).fit(Samples.threeSpeciesX, Samples.threeSpeciesT)

    assert(first.predict(Samples.threeSpeciesX) === second.predict(Samples.threeSpeciesX))
    assert(first.trees.map(_.rows) === second.trees.map(_.rows))
  }

  test("特徴量から指定した列だけを取り出す") {
    val features = Features(Vector("a", "b", "c"), Vector(0.1, 0.2, 0.3))

    assert(
      RandomForest.selectColumns(features, Vector("a", "c")) === Features(
        Vector("a", "c"),
        Vector(0.1, 0.3)
      )
    )
  }
```

`Features` は第 2 章の case class なので、`===` で中身の比較ができます。Java 版は `Features` を record にして同じことをしていますが、値が `double[]` なので比較のために `record` の `equals` を書き直しています。Scala の `Vector` は中身で比較されるので、そのまま期待値を書けます。

1 本分の情報（使った列・ブートストラップ標本の行番号・学習した木）は case class にまとめます。

```scala
// src/main/scala/machinelearning/chapter10/RandomForest.scala
/** 学習した 1 本の木と、その木が使った列・行。 */
case class FittedTree(columns: Vector[String], rows: Vector[Int], model: DecisionTree)

/** 第 3 章の決定木をブートストラップ標本と特徴量の部分集合で学習し、多数決で予測するランダムフォレスト。 */
class RandomForest(nEstimators: Int, maxFeatures: Int, maxDepth: Option[Int], seed: Long)
    extends Classifier:
  private var fittedTrees: Vector[FittedTree] = Vector.empty

  /** 学習した決定木。 */
  def trees: Vector[FittedTree] = fittedTrees

  override def fit(x: Vector[Features], t: Vector[String]): RandomForest =
    val random = Random(seed)
    val allColumns = x.head.columns
    fittedTrees = (0 until nEstimators).toVector.map { _ =>
      val rows = RandomForest.bootstrapSample(x.size, random)
      // Java 版（Collections.shuffle）と同じ手順で列を選ぶ
      val shuffled = RandomForest.shuffle(allColumns, random)
      val chosen = shuffled.take(maxFeatures).toSet
      val columns = allColumns.filter(chosen.contains)
      val sampleX = rows.map(row => RandomForest.selectColumns(x(row), columns))
      val sampleT = rows.map(t)
      val tree = maxDepth.fold(DecisionTree.unlimited())(DecisionTree.withMaxDepth)
      FittedTree(columns, rows, tree.fit(sampleX, sampleT))
    }
    this

  override def predict(x: Vector[Features]): Vector[String] =
    require(fittedTrees.nonEmpty, "fit で学習してから predict を呼んでください")
    RandomForest.majorityVote(
      fittedTrees.map(tree => tree.model.predict(RandomForest.selectColumns(x, tree.columns)))
    )

object RandomForest:
  /** 深さを制限しない決定木の森。 */
  def of(nEstimators: Int, maxFeatures: Int, seed: Long): RandomForest =
    RandomForest(nEstimators, maxFeatures, None, seed)

  /** 深さの上限を指定した決定木の森。 */
  def withMaxDepth(nEstimators: Int, maxFeatures: Int, maxDepth: Int, seed: Long): RandomForest =
    require(maxDepth >= 0, "深さの上限は 0 以上にしてください")
    RandomForest(nEstimators, maxFeatures, Some(maxDepth), seed)

  /** 特徴量から、指定した列だけを取り出す。 */
  def selectColumns(features: Features, columns: Vector[String]): Features =
    Features(columns, columns.map(features.value))

  def selectColumns(x: Vector[Features], columns: Vector[String]): Vector[Features] =
    x.map(selectColumns(_, columns))
```

- 深さの上限は `Option[Int]` で持ちます。Java 版は `-1` を「上限なし」の印にする定数 `UNLIMITED` を置きましたが、Scala では `None` がその意味を型で表します。木を作るところは `maxDepth.fold(DecisionTree.unlimited())(DecisionTree.withMaxDepth)` の 1 行で、`None` なら上限なしの木、`Some(n)` なら `withMaxDepth(n)` です
- 作り方の入口は、第 3 章の `DecisionTree.unlimited()`・`withMaxDepth(n)` と同じ名前でコンパニオンオブジェクトに置きました。`RandomForest(5, 1, None, 0)` と 4 つの値を並べるより、`RandomForest.of(5, 1, 0)` のほうが読めます
- 列の選択は `selectColumns` で「指定した列だけの `Features`」を作り直します。第 3 章の決定木は `Features.columns` の列だけから分割を探すので、絞った列だけで学習します
- `filter(chosen.contains)` で元の列の順に戻しておくと、同じ組み合わせが同じ並びになります

### 乱数の手順を Java 版にそろえる

木ごとの特徴量を選ぶところは、Java 版が `Collections.shuffle(list, random)` で列を並べ替えて先頭から `maxFeatures` 個を取ります。同じ乱数生成器でも、**乱数を何回どう使うか** が違えば結果は変わります。Scala の `scala.util.Random#shuffle` は `java.util.Random` を包めますが、内部の手順が `Collections.shuffle` と同じである保証はありません。そこで、Fisher-Yates の手順を `Collections.shuffle` と同じ向き（後ろから前へ）に自分で書きました。第 2 章の `Preprocessing.splitTrainTest` で分割の手順をそろえたのと同じ考え方です。

```scala
  /** Java の Collections.shuffle と同じ手順で並べ替える。 */
  private[chapter10] def shuffle[A](items: Vector[A], random: Random): Vector[A] =
    val array = items.toArray[Any]
    for i <- array.length - 1 to 1 by -1 do
      val j = random.nextInt(i + 1)
      val tmp = array(i)
      array(i) = array(j)
      array(j) = tmp
    array.toVector.map(_.asInstanceOf[A])
```

この関数の中だけは、配列を書き換える手続き的なコードです。並べ替えの手順そのものを Java と一致させることが目的なので、`Vector` で書き直さずに素直に写しました。公開範囲は `private[chapter10]` に絞り、この章の中からしか使えないようにしています。

第 3 章の `DecisionTree` には一切手を入れていません。`fit` と `predict` という小さな公開 API で作ってあったので、部品としてそのまま組み込めました。

```text
[info] RandomForestSpec:
[info] - 同数でなければ多数派の予測を選ぶ
[info] - 同数なら先に現れた予測を選ぶ
[info] - ブートストラップ標本は重複を許して同じ件数を選ぶ
[info] - 特徴量から指定した列だけを取り出す
[info] - 同じシードなら同じ森になる
[info] - 学習する前に予測するとエラーになる
```

## 10.6 特徴量の重要度

### 計算方法

決定木の各節で、

```text
減少量 = その節に届いた件数 × (その節のジニ不純度 − 分割後のジニ不純度)
```

を求め、分割に使った特徴量ごとに合計し、全体が 1 になるように割合にします。第 3 章の `Split.impurity` は「分割後のジニ不純度（件数で重み付けした平均）」なので、そのまま使えます。ジニ不純度も第 3 章の `DecisionTrees.gini` をそのまま使います。

### 決定木 1 本の重要度

```scala
class FeatureImportanceSpec extends AnyFunSuite:
  test("1 本の木の重要度は合計が 1 になる") {
    val tree = machinelearning.chapter03.DecisionTree
      .unlimited()
      .fit(Samples.threeSpeciesX, Samples.threeSpeciesT)

    val importances =
      FeatureImportance.treeImportances(
        tree.fitted.get,
        Samples.threeSpeciesX,
        Samples.threeSpeciesT
      )

    assert(importances.values.sum === 1.0 +- 1e-12)
  }

  test("分割に使われない特徴量の重要度は 0") {
    val columns = Vector("使う", "使わない")
    val x = Vector(
      Features(columns, Vector(0.1, 0.5)),
      Features(columns, Vector(0.9, 0.5))
    )
    val tree = machinelearning.chapter03.DecisionTree.unlimited().fit(x, Vector("左", "右"))

    val importances = FeatureImportance.treeImportances(tree.fitted.get, x, Vector("左", "右"))

    assert(importances("使わない") === 0.0)
    assert(importances("使う") === 1.0 +- 1e-12)
  }
```

第 3 章の `DecisionTree.fitted` は、学習前は `None` を返す `Option[Tree]` です。テストでは学習済みなので `.get` で取り出しています。

第 3 章の木は `enum Tree { case Leaf, Node }` なので、木をたどる処理はパターンマッチで書きます。Java 版の `switch` のパターンマッチ、Kotlin 版の `when (tree) { is Leaf -> … }` に当たります。

```scala
// src/main/scala/machinelearning/chapter10/FeatureImportance.scala
/** 分割で減った不純度から、特徴量の重要度を求める。 */
object FeatureImportance:

  /** 1 本の木の重要度。合計が 1 になるように正規化する。 */
  def treeImportances(tree: Tree, x: Vector[Features], t: Vector[String]): Map[String, Double] =
    normalize(x.head.columns, impurityDecreases(tree, x, t))

  /** 1 回の分割で減った不純度（件数で重み付け）を、木全体から集める。 */
  private def impurityDecreases(
      tree: Tree,
      x: Vector[Features],
      t: Vector[String]
  ): Vector[(String, Double)] =
    tree match
      case Leaf(_) => Vector.empty
      case Node(split, left, right) =>
        val (leftRows, rightRows) =
          x.indices.toVector.partition(i => x(i).value(split.feature) <= split.threshold)
        val here = split.feature -> t.size * (DecisionTrees.gini(t) - split.impurity)
        here +:
          (impurityDecreases(left, leftRows.map(x), leftRows.map(t))
            ++ impurityDecreases(right, rightRows.map(x), rightRows.map(t)))

  private def normalize(
      columns: Vector[String],
      decreases: Vector[(String, Double)]
  ): Map[String, Double] =
    val total = decreases.map(_._2).sum
    columns.map { column =>
      val amount = decreases.filter(_._1 == column).map(_._2).sum
      column -> (if total == 0.0 then 0.0 else amount / total)
    }.toMap
```

- 「特徴量と減少量」の組は、Java 版が名前付きの record `Decrease` を作ったところです。Scala にはタプルがあるので `(String, Double)` のままで、`split.feature -> 減少量` と書けます。F# 版がタプルのリストで書いたのと同じです
- 左右の行番号は `partition` で 1 回の走査で分けます。Java 版は左を `filter` で作ってから「左に入らなかったもの」を右にしていたので、`contains` の繰り返しが入っていました
- `Leaf(_)` は、ラベルを使わないことを `_` で示します。Java 版は変数名を `ignored` にして PMD の「使っていないローカル変数」の指摘をかわし、Java 22 以降なら `case Leaf _ ->` と書けると註を付けていました。Scala 3 のパターンでは最初から `_` が書けます
- 結果は `Map[String, Double]` です。Java 版・Kotlin 版は「列の順に並ぶ `LinkedHashMap`」を返して表示の順も保証しましたが、Scala の `Map` は順を保証しません。そのため表示するときは `x.head.columns` の順に引く（10.9 節の `Main`）という分担にしています

### ランダムフォレストの重要度

森の重要度は、木ごとの重要度（その木が学習に使ったブートストラップ標本で計算）の平均を、もう一度割合に直したものです。

```scala
  /** 森の重要度。木ごとに正規化した重要度の平均を取り、最後にもう一度正規化する。 木が使わなかった特徴量は、その木では 0 とする。
    */
  def forestImportances(
      forest: RandomForest,
      x: Vector[Features],
      t: Vector[String]
  ): Map[String, Double] =
    val columns = x.head.columns
    val totals = forest.trees.foldLeft(columns.map(_ -> 0.0).toMap) { (acc, fitted) =>
      val sampleX = fitted.rows.map(row => RandomForest.selectColumns(x(row), fitted.columns))
      val sampleT = fitted.rows.map(t)
      val importances = fitted.model.fitted
        .map(treeImportances(_, sampleX, sampleT))
        .getOrElse(Map.empty[String, Double])
      importances.foldLeft(acc) { (sums, entry) =>
        val (feature, value) = entry
        sums.updated(feature, sums.getOrElse(feature, 0.0) + value / forest.trees.size)
      }
    }
    val total = totals.values.sum
    if total == 0.0 then totals else totals.map((feature, value) => feature -> value / total)
```

全部の列を 0 にした `Map` から始めて、木ごとの重要度を足し込む `foldLeft` です。木ごとの重要度には、その木が使った列しか入っていないので、使わなかった列は 0 のまま残ります。

### 正規化の順を間違えて値がずれた

ここは一度間違えました。最初は「木ごとの減少量をそのまま全部足し合わせ、最後に 1 回だけ正規化する」と書いたのです。式としては自然に見えますが、Java 版・Kotlin 版・Python 版はどれも「木ごとに正規化してから平均し、最後にもう一度正規化する」手順です。実データのテストで、重要度だけが Java 版と合いませんでした。

```text
[info] IrisDataSpec:
[info] - 実行するとモデルごとの正解率と特徴量の重要度を表示する *** FAILED ***
[info]   Analysis:
[info]   Vector1(7: "がく片長さ	0.1892" -> "がく片長さ	0.1882", 8: "がく片幅	0.1259" -> "がく片幅	0.1271", 9: "花弁長さ	0.2684" -> "花弁長さ	0.2708", 10: "花弁幅	0.4165" -> "花弁幅	0.4140")
```

ScalaTest は `Vector` どうしの比較が失敗すると、違う要素だけを `Analysis` に並べます。正解率の 4 行はすべて一致していて、重要度の 4 行だけがずれていることが一目で分かりました。

2 つの手順は、**木ごとの重みが違います**。減少量をそのまま足すと、「件数 × 不純度の減り」の絶対値が大きい木（深く育った木、分けやすい標本を引いた木）の影響が強くなります。木ごとに正規化してから平均すると、100 本の木が 1 票ずつ持ちます。ランダムフォレストは「弱い木の多数決」なので、票の重みをそろえる後者を採ります。

手順をそろえたところ、Java 版と完全に一致しました。1 つの言語だけで書いていたら「どちらも正規化しているから大丈夫」と流していたはずで、**同じ手順を別の言語で書いた実装が正解の照合先になる** ことが効きました。

```text
[info] FeatureImportanceSpec:
[info] - 1 本の木の重要度は合計が 1 になる
[info] - 分割に使われない特徴量の重要度は 0
```

## 10.7 モデル共通の約束

### trait で「fit と predict を持つもの」を表す

決定木・ロジスティック回帰・ランダムフォレストは、どれも `fit(x, t)` と `predict(x)` を持っています。これを `trait` で表します。

```scala
// src/main/scala/machinelearning/chapter10/Classifier.scala
/** 分類器の約束。学習して、予測する。 */
trait Classifier:
  /** 訓練データから学習する。 */
  def fit(x: Vector[Features], t: Vector[String]): Classifier

  /** 特徴量ごとのラベルを予測する。 */
  def predict(x: Vector[Features]): Vector[String]

object Classifier:
  /** 訓練データとテストデータの正解率を求める。 */
  def score(
      model: Classifier,
      xTrain: Vector[Features],
      tTrain: Vector[String],
      xTest: Vector[Features],
      tTest: Vector[String]
  ): (Double, Double) =
    val fitted = model.fit(xTrain, tTrain)
    (
      KinokoTakenoko.accuracy(fitted.predict(xTrain), tTrain),
      KinokoTakenoko.accuracy(fitted.predict(xTest), tTest)
    )
```

- 正解率は第 1 章の `KinokoTakenoko.accuracy` を再利用しています
- 訓練データとテストデータの正解率は、タプル `(Double, Double)` で返します。Java 版は `record Score(double train, double test)`、F# 版はレコード `{ Train; Test }` を作りました。Scala でも case class を作れますが、この章では受け取り側が `val (train, test) = …` と分解するだけなので、名前を付けずにタプルで返しています
- `fit` の戻り値をそのまま `fitted` として使い、`predict` を呼んでいます。`fit` が `this` を返す設計なので同じオブジェクトですが、「学習してから予測する」という順序が式の形に出ます

### 名前的部分型とアダプター

`LogisticRegression` と `RandomForest` は `fit` と `predict` を持っていますが、`Classifier` を継承すると **宣言していない** 間は `Vector[Classifier]` に入りません。

```text
[error] -- [E007] Type Mismatch Error: .../src/test/scala/machinelearning/chapter10/ClassifierSpec.scala:10:24
[error] 10 |      LogisticRegression(),
[error]    |      ^^^^^^^^^^^^^^^^^^^^
[error]    |      Found:    machinelearning.chapter10.LogisticRegression
[error]    |      Required: machinelearning.chapter10.Classifier
```

Scala の `trait` も、Java・Kotlin の `interface` と同じく名前で型を判定する **名前的部分型**（nominal subtyping）だからです。この 2 つはこの章で書いたクラスなので、`extends Classifier` を宣言し、`fit` と `predict` に `override` を付けます。Scala では、`trait` のメソッドを実装するときの `override` は任意ですが、付けておくと名前や引数の型を変えたときにコンパイラが知らせます。

第 3 章の `DecisionTree` には手を入れない方針なので、`DecisionTree` を包んで `Classifier` として振る舞わせるクラス（**アダプター**）を書きます。

```scala
// src/main/scala/machinelearning/chapter10/DecisionTreeClassifier.scala
/** 第 3 章の決定木を、分類器の約束に合わせるアダプター。第 3 章のコードは変更しない。 */
class DecisionTreeClassifier(tree: DecisionTree) extends Classifier:
  override def fit(x: Vector[Features], t: Vector[String]): DecisionTreeClassifier =
    val _ = tree.fit(x, t)
    this

  override def predict(x: Vector[Features]): Vector[String] = tree.predict(x)

object DecisionTreeClassifier:
  def withMaxDepth(maxDepth: Int): DecisionTreeClassifier =
    DecisionTreeClassifier(DecisionTree.withMaxDepth(maxDepth))

  def unlimited(): DecisionTreeClassifier = DecisionTreeClassifier(DecisionTree.unlimited())
```

- `fit` の戻り値の型は、`Classifier` ではなく `DecisionTreeClassifier` です。Scala も Java・Kotlin と同じく、オーバーライドするメソッドの戻り値を元の型のサブタイプに狭められます（共変戻り値型）
- `val _ = tree.fit(x, t)` の `val _ =` は、第 3 章の `fit` が返す `DecisionTree` を使わないことを示す書き方です。この章では `-Wvalue-discard` の指摘は出ませんでしたが（式文として捨てた値は対象外です）、「返ってきた木は使わず、自分自身を返す」という意図を読み手に示すために残しました
- ファクトリーメソッドは、第 3 章の `DecisionTree.unlimited()`・`withMaxDepth(n)` と同じ名前にそろえました。包む側と包まれる側で作り方が同じなので、読み手は第 3 章の知識のまま使えます

4 つの言語で、この「共通の約束」の表し方が分かれます。

| 観点 | Python の `Protocol` | Scala の `trait`・Java/Kotlin の `interface` | F# の関数の型 |
|------|--------------------|-----------------------------------|-------------|
| 型が合う条件 | 同じ名前・型のメソッドを持っている（構造的部分型） | 継承を宣言している（名前的部分型） | 引数と戻り値の形が合っている |
| 既存のクラス（第 3 章の決定木） | そのまま入れられる | アダプターで包む | 関数で包む |
| 型の確認 | mypy を実行したとき | コンパイルのとき | コンパイルのとき |
| テスト用の単純なモデル | クラスを 1 つ書く | クラスを 1 つ書く | ラムダ式 1 つ |

Scala には構造的部分型（`{ def fit(…): … }` のような型）もありますが、実行時にリフレクションを使うため、この章では素直に `trait` を選びました。

## 10.8 Tribuo との突き合わせについて

Java 版・Kotlin 版は、この章で Tribuo の `LogisticRegressionTrainer` と `RandomForestTrainer` をアダプターで包み、自作のモデルと正解率を比べています。Scala 版の実装には、まだこの比較が入っていません。

突き合わせの結果と、そこで分かった設定の違い（ロジスティック回帰のエポック数、ランダムフォレストで特徴量を選ぶ単位、決定木を分割する最小の件数 `minChildWeight`）は、[Java 版の 10.8 節・10.10 節](../java/10-logistic-regression-and-ensemble.md) と [Kotlin 版の 10.8 節](../kotlin/10-logistic-regression-and-ensemble.md) を参照してください。Java 版は同じ分割・同じ乱数で動かしているので、次の節で示す自作の数値と直接比べられます。

この章で押さえておきたいのは、次の一点です。同じ「ランダムフォレスト」という名前でも、特徴量を選ぶ単位（木ごとか分割ごとか）や分割を止める条件が違えば、正解率は変わります。ライブラリの結果と比べるときは、名前ではなく設定をそろえて比べます。

## 10.9 実データで突き合わせる

### モデルを比べる

`Main.run` で、iris の訓練データ・テストデータでの正解率と、ランダムフォレストの特徴量の重要度を表示します。第 1 章から続けている「表示する関数を引数で受け取る」形なので、テストで出力をそのまま確かめられます。

```scala
// src/main/scala/machinelearning/chapter10/Main.scala
/** モデルごとの正解率と、ランダムフォレストの特徴量の重要度を表示する。 */
object Main:
  private val TestSize = 0.3
  private val Seed = 0L
  private val NEstimators = 100
  private val MaxFeatures = 2
  private val ShallowDepth = 2

  /** 名前とモデル。表示する順に並べる。 */
  def models(): Vector[(String, Classifier)] = Vector(
    s"決定木（深さ $ShallowDepth）" -> DecisionTreeClassifier.withMaxDepth(ShallowDepth),
    "ロジスティック回帰" -> LogisticRegression(),
    s"ランダムフォレスト（$NEstimators 本）" -> RandomForest.of(NEstimators, MaxFeatures, Seed),
    s"ランダムフォレスト（$NEstimators 本・深さ $ShallowDepth）" ->
      RandomForest.withMaxDepth(NEstimators, MaxFeatures, ShallowDepth, Seed)
  )

  def run(print: String => Unit): Unit =
    val split = Preprocessing.prepareIris(Paths.get(DataDir.current(), "iris.csv"), TestSize, Seed)
    print("モデル\t訓練データ\tテストデータ")
    models().foreach { (name, model) =>
      val (train, test) =
        Classifier.score(model, split.xTrain, split.tTrain, split.xTest, split.tTest)
      print(s"$name\t${format(train)}\t${format(test)}")
    }

    val forest = RandomForest.of(NEstimators, MaxFeatures, Seed).fit(split.xTrain, split.tTrain)
    print("")
    print(s"ランダムフォレスト（$NEstimators 本）の特徴量の重要度:")
    val importances = FeatureImportance.forestImportances(forest, split.xTrain, split.tTrain)
    split.xTrain.head.columns.foreach(column => print(s"$column\t${format(importances(column))}"))

  private def format(value: Double): String = String.format(Locale.ROOT, "%.4f", value)
```

- 名前とモデルの組は `Vector[(String, Classifier)]` です。Java 版は標準の組の型が無いので `LinkedHashMap` で順を保ちました。Kotlin 版の `List<Pair<String, Classifier>>` と同じ書き方になります
- `models().foreach { (name, model) => … }` で、タプルを 2 つの引数として受け取ります。`Classifier.score` が返すタプルも `val (train, test) = …` で分解します。名前付きの型を作らなくても、受け取る場所で名前が付きます
- 重要度は `Map` なので順が定まりません。表示は `split.xTrain.head.columns` の順に引いて、列の順に並べています
- `String.format(Locale.ROOT, "%.4f", value)` は、環境によって小数点が `,` にならないようにするためです（第 3 章と同じ）

```bash
sbt "run chapter10"
```

```text
モデル	訓練データ	テストデータ
決定木（深さ 2）	0.9333	0.9556
ロジスティック回帰	0.9143	0.9111
ランダムフォレスト（100 本）	1.0000	0.9333
ランダムフォレスト（100 本・深さ 2）	0.9429	0.9556

ランダムフォレスト（100 本）の特徴量の重要度:
がく片長さ	0.1882
がく片幅	0.1271
花弁長さ	0.2708
花弁幅	0.4140
```

**この 8 つの数値は、[Java 版の 10.10 節](../java/10-logistic-regression-and-ensemble.md) の自作のモデルの数値と完全に一致します。** 分割（第 2 章の `java.util.Random` + Fisher-Yates）、ブートストラップ標本の引き方、列の並べ替えの手順をすべて Java 版にそろえたからです。言語もコレクションも書き方も違うのに、同じ手順を踏めば浮動小数点の計算まで同じ結果になる、という確認になりました。

結果から読み取れることは次のとおりです。

- **アンサンブルがいつも勝つわけではない**。深さを制限しないランダムフォレスト（0.9333）は、第 3 章の深さ 2 の決定木（0.9556）より低くなりました。木 1 本 1 本が訓練データを分け切るまで深く育ち（訓練データの正解率 1.0000）、iris のような小さなデータでは多数決でも過学習を打ち消し切れていません。木の深さを 2 に制限した森は、決定木と同じ 0.9556 です
- **テストデータ 45 件では、1 件の違いが 0.0222 の差になる**。1 回の分割で測った正解率の小さな差でモデルの優劣を決めるのは危険です。分け方を変えて何度も測る交差検証は第 11 章で扱います
- **ランダムフォレストの重要度は、複数の特徴量に分散する**。木ごとに使える特徴量を 2 つに絞っているので、花弁幅を使えない木は別の特徴量で分割するためです。第 3 章の深さ 2 の木は、2 回とも花弁幅で分割していました

### 実データのテスト

この表示は、第 1 章から続けている形のテストで固定します。値は実装を実データで動かして得たものなので、Red を経ていません。

```scala
// src/test/scala/machinelearning/chapter10/IrisDataSpec.scala
class IrisDataSpec extends AnyFunSuite:
  private val csvFile = Paths.get(DataDir.current(), "iris.csv")

  private def requireData(): org.scalatest.Assertion =
    assume(Files.exists(csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）")

  test("実行するとモデルごとの正解率と特徴量の重要度を表示する") {
    requireData(): Unit
    val output = Vector.newBuilder[String]

    Main.run(output += _)

    // 自作のモデルの数値は Java 版と一致する（分割も乱数も同じ手順のため）
    assert(
      output.result() === Vector(
        "モデル\t訓練データ\tテストデータ",
        "決定木（深さ 2）\t0.9333\t0.9556",
        "ロジスティック回帰\t0.9143\t0.9111",
        "ランダムフォレスト（100 本）\t1.0000\t0.9333",
        "ランダムフォレスト（100 本・深さ 2）\t0.9429\t0.9556",
        "",
        "ランダムフォレスト（100 本）の特徴量の重要度:",
        "がく片長さ\t0.1882",
        "がく片幅\t0.1271",
        "花弁長さ\t0.2708",
        "花弁幅\t0.4140"
      )
    )
  }
```

`requireData(): Unit` の `: Unit` は、`assume` が返す `Assertion` を捨てていることを示す型注釈です。`-Wvalue-discard` を有効にしているので、この注釈が無いと「値を黙って捨てている」としてコンパイルが止まります（第 1 章）。

データが無い環境では、`assume` がテストをキャンセルします。

```bash
ML_DATA_DIR=/nonexistent sbt "testOnly machinelearning.chapter10.*"
```

```text
[info] IrisDataSpec:
[info] - 実行するとモデルごとの正解率と特徴量の重要度を表示する !!! CANCELED !!!
[info]   java.nio.file.Files.exists(IrisDataSpec.this.csvFile) was false 学習データ iris.csv が配置されていない（gulp data:setup） (IrisDataSpec.scala:11)
...
[info] Total number of tests run: 15
[info] Suites: completed 4, aborted 0
[info] Tests: succeeded 15, failed 0, canceled 1, ignored 0, pending 0
[info] All tests passed.
```

第 10 章のテストは全 16 件です。データがあれば 16 件すべてが通り、データが無ければ実データのテスト 1 件がキャンセルされて 15 件が通ります。

## 10.10 Notebook で探索する

Scala 版では Notebook と可視化を扱いません。モデルごとの正解率のグラフ、ロジスティック回帰の損失の推移、モデル別の特徴量の重要度、森の大きさと正解率の関係は、[Python 版の 10.11 節](../python/10-logistic-regression-and-ensemble.md) と [Kotlin 版の 10.11 節](../kotlin/10-logistic-regression-and-ensemble.md) の可視化を参照してください。

Scala 版の `LogisticRegression#losses`（繰り返しごとの損失の `Vector`）と `FeatureImportance.forestImportances`（特徴量ごとの重要度の `Map`）は、ほかの版と同じ形のデータを返すので、同じ観点で読めます。Java 版と分割も乱数も同じなので、値そのものも Java 版と一致します。

## 10.11 まとめ

この章では、ロジスティック回帰とランダムフォレストを自作し、共通の `trait` でまとめて評価しました。

1. **数値として正しい実装** — ソフトマックス関数は、定義どおりでは大きな値で `NaN` になった。JVM は警告を出さないので、境界の値のテストで見つけ、最大値を引く方法で直した。ScalaTest の `assert` は脱糖された式をそのまま失敗メッセージに出すので、どの計算が `false` だったかが読める
2. **畳み込みで書く勾配降下法** — Java 版の三重の `for` 文を、`Vector` の `map`・`zip`・`foldLeft` に置き換えた。重みは不変の `Vector` を作り直して `var` に入れ替える持ち方にした。既定引数のおかげで、テストでは変えたい引数だけを名前付きで渡せる
3. **部品の再利用** — 第 3 章の決定木を変更せずに組み合わせ、ブートストラップ標本と特徴量の部分集合でランダムフォレストを作った。深さの上限は `Option[Int]` で表し、Java 版の `-1` という印が要らなくなった
4. **名前的部分型とアダプター** — Scala の `trait` も継承の宣言が必要なので、第 3 章の決定木をアダプターで包み、自作のモデルと同じ `Classifier.score` で評価した。F# 版が関数の型で済ませたところとの違いが出た
5. **手順をそろえると数値が一致する** — 乱数生成器だけでなく、並べ替えの手順（`Collections.shuffle` と同じ Fisher-Yates）までそろえたので、8 つの数値が Java 版と完全に一致した。特徴量の重要度の正規化の順を間違えたときも、Java 版の値が照合先になって気づけた

iris のテストデータ 45 件では、どのモデルも数件の差しかなく、1 回の分割での比較ではどのモデルが優れているとも言い切れません。次の章では、正解率以外の評価指標と、データの分け方を変えて何度も測る交差検証を学びます。
