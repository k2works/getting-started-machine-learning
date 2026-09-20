---
type: Article
title: "第 11 章: 評価指標と交差検証"
description: "混同行列・適合率・再現率・F 値・MSE と K 分割交差検証を、評価関数を型の別名 Metric（関数そのもの）として渡す設計と遅延評価の LazyList で Scala の TDD で自作し、Tribuo の LabelEvaluator・RegressionEvaluator・KFoldSplitter と突き合わせる。"
tags: [article,getting-start-ml,scala]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T03:45:00Z }
---

# 第 11 章: 評価指標と交差検証

## 11.1 はじめに

これまでの章では、分類モデルを正解率で、回帰モデルを決定係数や誤差で評価してきました。しかし、1 つの指標と 1 回だけの訓練・テスト分割で「良いモデル」と判断すると、見落としが生まれます。

この章では、次の 2 つを TDD で自作し、Tribuo の評価器と突き合わせます。

- **評価指標**: 分類の混同行列・適合率・再現率・F 値と、回帰の MSE・RMSE・MAE
- **K 分割交差検証**: データを K 個に分け、訓練とテストを K 回入れ替えて評価する方法

あわせて、「どの指標で評価するか」を関数として受け渡す設計を学びます。評価の手順（分割して学習し、予測して採点する）を 1 つの関数にまとめ、採点に使う関数だけを差し替えられるようにします。

[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md) と同じ題材を、同じ TODO リストで進めます。[Kotlin 版](../kotlin/11-evaluation-metrics-and-cross-validation.md) は評価関数を `(List<T>, List<T>) -> Double` という関数型で表し、結果を `Sequence` で返しました。[Java 版](../java/11-evaluation-metrics-and-cross-validation.md) には関数型の構文が無いので、`java.util.function` の関数型インターフェース `ToDoubleBiFunction` に名前を付け、結果を `DoubleStream` で返しました。Scala は Kotlin と同じく関数そのものが型なので、**型の別名（`type`）** だけで済み、結果は遅延評価の **`LazyList`** で返します。[F# 版](../fsharp/11-evaluation-metrics-and-cross-validation.md) の関数型の書き方とも対比します。

## 11.2 正解率だけでは足りない理由

`Survived.csv` はタイタニック号の乗客のデータで、生存（`Survived` が 1）した人は死亡（0）した人より少なくなっています。このように正解ラベルの数が偏っていると、全員を「死亡」と予測するだけのモデルでも、正解率はそれなりに高くなります。このモデルは生存者を 1 人も見つけられないのに、正解率だけを見ると当たっているように見えます（件数は [Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md) を参照してください）。

そこで、予測の当たり外れを 4 つに分けて数える **混同行列** を使います。ここでは「生存」を正例（見つけたいほう）とします。

| | 正例と予測 | 負例と予測 |
|---|-----------|-----------|
| **実際は正例** | TP（真陽性） | FN（偽陰性） |
| **実際は負例** | FP（偽陽性） | TN（真陰性） |

混同行列から、目的に応じた指標を求めます。

| 指標 | 式 | 意味 |
|------|-----|------|
| 適合率（precision） | TP / (TP + FP) | 正例と予測したうち、本当に正例だった割合 |
| 再現率（recall） | TP / (TP + FN) | 本当の正例のうち、正例と予測できた割合 |
| F 値（F1） | 2 × 適合率 × 再現率 / (適合率 + 再現率) | 適合率と再現率の調和平均 |

回帰では、予測と正解の差（誤差）を集計します。

| 指標 | 意味 |
|------|------|
| MSE（平均二乗誤差） | 誤差の 2 乗の平均 |
| RMSE（平均二乗誤差の平方根） | MSE の平方根。正解と同じ単位になる |
| MAE（平均絶対誤差） | 誤差の絶対値の平均 |

RMSE と MAE は、第 7 章で `RegressionMetrics.rootMeanSquaredError`・`meanAbsoluteError` として作りました。この章ではそれを再利用し、MSE だけを足します。

## 11.3 TODO リストの作成

**TODO リスト**:

- [ ] 混同行列を数える
  - [ ] 正例と負例の当たり外れを数える
  - [ ] どちらのラベルを正例にするかを指定できる
  - [ ] 正解と予測の件数が違えばエラーにする
- [ ] 適合率・再現率・F 値を求める
  - [ ] 分母が 0 のときは 0 にする
- [ ] MSE を求め、第 7 章の RMSE・MAE と並べる
- [ ] K 分割のテストデータを作る
  - [ ] ほぼ均等な件数に分ける
  - [ ] どの行もちょうど一度だけテストデータになる
  - [ ] シードで分け方が決まる
- [ ] 交差検証で分割ごとのスコアを求める
  - [ ] 評価関数を差し替えられる
  - [ ] 必要な分だけ学習する（遅延評価）
- [ ] 混同行列の指標を、正解と予測から求める評価関数に変える
- [ ] Tribuo の評価器と結果を突き合わせる
- [ ] 実データで交差検証の平均を表示する

## 11.4 混同行列を数える

### Red: 最初のテスト

最初のテストは、2 件の正解と予測から混同行列を数えるものです。ラベルは架空の文字列 `"1"`・`"0"` にし、`"1"` を正例とします。

```scala
// src/test/scala/machinelearning/chapter11/MetricsSpec.scala
class MetricsSpec extends AnyFunSuite:
  private val actual = Vector("1", "1", "0", "0", "1")
  private val predicted = Vector("1", "0", "0", "1", "1")

  test("すべて正解なら真陽性と真陰性だけを数える") {
    assert(
      ConfusionMatrix.of(Vector("1", "0"), Vector("1", "0"), "1") === ConfusionMatrix(1, 0, 0, 1)
    )
  }
```

`ConfusionMatrix` がまだ無いので、コンパイルできないことが最初の Red です。

### Green: 仮実装

混同行列は 4 つの件数を持つだけの値なので、**case class** にします。Kotlin 版の `data class`・Java 版の `record` と同じく、`equals`・`hashCode`・`toString` が中身で比べる形になります。成分が `Int` なので、第 2 章の `Features` や第 7 章の `Matrix` で配列が引き起こした問題（既定の等価判定が参照を比べる）はありません。

正解と予測を受け取る生成用のメソッドは、コンパニオンオブジェクトに置きます。ラベルの型は分類によって違うので、**型引数 `[A]`** を付けます。まずは期待値をそのまま返す仮実装です。

```scala
object ConfusionMatrix:
  def of[A](actual: Vector[A], predicted: Vector[A], positive: A): ConfusionMatrix =
    ConfusionMatrix(1, 0, 0, 1)
```

### 三角測量

外れた予測を含むテストを足して、仮実装を一般化させます。

```scala
  test("外れた予測を偽陽性と偽陰性に分けて数える") {
    assert(ConfusionMatrix.of(actual, predicted, "1") === ConfusionMatrix(2, 1, 1, 1))
  }
```

```text
MetricsSpec:
- 外れた予測を偽陽性と偽陰性に分けて数える *** FAILED ***
  ConfusionMatrix(1, 0, 0, 1) did not equal ConfusionMatrix(2, 1, 1, 1) (MetricsSpec.scala:17)
  Analysis:
  ConfusionMatrix(fn: 0 -> 1, fp: 0 -> 1, tp: 1 -> 2)
```

ScalaTest の `===` は、case class どうしの比較で **Analysis** の行を出し、どのフィールドが違うかを名前で並べます。Java 版の AssertJ は record の `toString` を 2 行並べて見せましたが、Scala 版は違うフィールドだけが残るので、4 つの件数のどれがずれたのかが一目で分かりました。

数え方は、正解と予測を組にして畳み込みます。

```scala
// src/main/scala/machinelearning/chapter11/Metrics.scala
object ConfusionMatrix:
  /** 正解と予測を 1 件ずつ比べて数える。positive と等しいラベルを正例、それ以外を負例とする。 */
  def of[A](actual: Vector[A], predicted: Vector[A], positive: A): ConfusionMatrix =
    Metrics.requireSameSize(actual, predicted)
    actual.lazyZip(predicted).foldLeft(ConfusionMatrix(0, 0, 0, 0)) { (cm, pair) =>
      (pair._1 == positive, pair._2 == positive) match
        case (true, true)   => cm.copy(tp = cm.tp + 1)
        case (false, true)  => cm.copy(fp = cm.fp + 1)
        case (true, false)  => cm.copy(fn = cm.fn + 1)
        case (false, false) => cm.copy(tn = cm.tn + 1)
    }
```

- Java 版は 4 つの `int` のローカル変数を `if`・`else if` で増やしました。Scala 版は case class の `copy` で「1 つだけ増やした新しい値」を作り、`foldLeft` で畳み込みます。途中で書き換わる変数がありません
- 「実際に正例か」と「正例と予測したか」の 2 つの真偽値を **タプルのパターンマッチ** で 4 通りに分けます。`if` の連鎖と違い、4 つの場合がすべて書かれていることが形から読めます
- `lazyZip` は中間のコレクションを作らずに 2 つの `Vector` を組にします。Kotlin 版の `zip` と同じ役割です
- ラベルの比較は `==` です。Scala の `==` は `equals` を呼ぶので、Java 版が `Integer` の箱詰めで `equals` を使わなければならなかった落とし穴はありません

「どちらを正例にするか」でも数え方が変わることを、もう 1 つのテストで固定します。

```scala
  test("正例を入れ替えると真陽性と真陰性が入れ替わる") {
    assert(ConfusionMatrix.of(actual, predicted, "0") === ConfusionMatrix(1, 1, 1, 2))
  }
```

### 件数が違うときは黙って切り詰めない

正解と予測の件数が違うのは、呼び出し側の誤りです。エラーになることをテストで確かめます。

```scala
  test("正解と予測の件数が違えばエラーになる") {
    val thrown = intercept[IllegalArgumentException](ConfusionMatrix.of(actual, Vector("1"), "1"))

    assert(thrown.getMessage.contains("正解 5 件、予測 1 件"))
  }
```

`lazyZip` は短いほうに合わせて切り詰めるので、確かめずに数えると余った予測を黙って無視してしまいます。Kotlin 版の `zip` と同じ落とし穴です。件数を確かめる関数を `Metrics` に置き、`of` の最初で呼びます。この関数は MSE と正解率でも使います。

```scala
  /** 正解と予測の件数が同じでなければ例外を投げる。短いほうに合わせて黙って切り詰めない。 */
  private[chapter11] def requireSameSize(actual: Vector[?], predicted: Vector[?]): Unit =
    require(
      actual.size == predicted.size,
      s"正解と予測の件数が違います（正解 ${actual.size} 件、予測 ${predicted.size} 件）"
    )
```

- 要素の型を問わず件数だけを見るので、型引数は `Vector[?]`（**ワイルドカード**）です。Kotlin 版の `List<*>`・Java 版の `List<?>` と同じ意味です
- `require` は失敗すると `IllegalArgumentException` を投げます。Java 版が `if` と `throw` で書いた 4 行が 1 つの式になります
- `private[chapter11]` は「このパッケージの中だけで使える」という可視性です。Java 版のパッケージプライベート（修飾子なし）に当たりますが、Scala はどのパッケージから見えるかを明示します

## 11.5 適合率・再現率・F 値

### 明白な実装

3 つの指標は式が決まっているので、仮実装をはさまずに書きます。

```scala
  private def ratio(numerator: Double, denominator: Double): Double =
    if denominator == 0 then 0.0 else numerator / denominator

  /** 適合率。正例と予測したうち、本当に正例だった割合。 */
  def precision(cm: ConfusionMatrix): Double = ratio(cm.tp, cm.tp + cm.fp)

  /** 再現率。本当の正例のうち、正例と予測できた割合。 */
  def recall(cm: ConfusionMatrix): Double = ratio(cm.tp, cm.tp + cm.fn)

  /** F 値。適合率と再現率の調和平均。 */
  def f1Score(cm: ConfusionMatrix): Double =
    val p = precision(cm)
    val r = recall(cm)
    ratio(2 * p * r, p + r)
```

引数は `Int` ですが、`ratio` の引数が `Double` なので自動で変換されます。整数の割り算になって 0 に落ちる心配はありません。

### 分母が 0 になる場合

正例を 1 件も予測しなければ、適合率の分母（TP + FP）は 0 です。0 で割ると `Double` では `NaN` になり、平均を取ると全体が `NaN` に伝染します。そこで `ratio` は分母が 0 なら 0 を返します。この約束は Tribuo の評価器とも一致することを、11.9 節で確かめます。

```scala
  test("正例を 1 件も予測しなければ適合率も再現率も F 値も 0 になる") {
    val cm = ConfusionMatrix(0, 0, 2, 3)

    assert(
      (Metrics.precision(cm), Metrics.recall(cm), Metrics.f1Score(cm)) === (0.0, 0.0, 0.0)
    )
  }
```

3 つの値をタプルにして 1 回で比べています。Java 版が AssertJ の `containsExactly` でリストにしたところを、Scala ではタプルの等価判定がそのまま使えます。

## 11.6 回帰の評価指標

MSE は第 7 章に無いので、この章で足します。第 7 章の `RegressionMetrics` は変更せず、`Metrics` に置きます。

```scala
  /** 平均二乗誤差（MSE）。誤差の 2 乗の平均。 */
  def meanSquaredError(actual: Vector[Double], predicted: Vector[Double]): Double =
    requireSameSize(actual, predicted)
    actual.lazyZip(predicted).map((t, y) => (y - t) * (y - t)).sum / actual.size

  /** 正解率。正解と予測が一致した割合。 */
  def accuracy[A](actual: Vector[A], predicted: Vector[A]): Double =
    requireSameSize(actual, predicted)
    actual.lazyZip(predicted).count(_ == _).toDouble / actual.size
```

`count(_ == _)` は、組の 2 つの値を受け取って比べます。`lazyZip` が 2 引数の関数を受け付けるので、タプルを開く `case (a, b) =>` を書かずに済みます。

### 学習用テスト: 外れた予測への敏感さ

MSE が「大きく外れた 1 件」に敏感であることを、テストで確かめます。

```scala
  test("平均二乗誤差は外れた予測に敏感で平均絶対誤差より大きく増える") {
    val t = Vector(1.0, 2.0, 3.0, 4.0)
    val small = Vector(1.5, 2.5, 3.5, 4.5)
    val one = Vector(1.0, 2.0, 3.0, 6.0)

    assert(Metrics.meanSquaredError(t, small) === 0.25 +- 1e-12)
    assert(Metrics.meanSquaredError(t, one) === 1.0 +- 1e-12)
  }
```

4 件すべてが 0.5 ずつ外れた予測の MSE は 0.25 ですが、3 件は完全に当たり 1 件だけ 2.0 外れた予測の MSE は 1.0 になります。誤差の合計はどちらも 2.0 なのに、MSE は 4 倍違います。これが「RMSE が MAE より大きければ、大きく外れた予測がある」と読める理由です。

## 11.7 K 分割交差検証

### なぜ分割を入れ替えるのか

1 回の訓練・テスト分割では、たまたまテストデータに簡単な行が集まれば良いスコアが、難しい行が集まれば悪いスコアが出ます。K 分割交差検証は、データを K 個のかたまりに分け、そのうち 1 つをテストデータ、残りを訓練データにして K 回評価し、平均を取ります。どの行もちょうど 1 回だけテストデータになります。

### 分け方を表す case class と K 分割

分け方は、行の位置（0 始まり）を訓練用とテスト用に分けて持つだけの値です。

```scala
// src/main/scala/machinelearning/chapter11/CrossValidation.scala
case class Fold(train: Vector[Int], test: Vector[Int])
```

Java 版は record の中で `List.copyOf` を呼んで、渡されたリストが後から変わらないように守りました。Scala の `Vector` は不変なので、守るコードが要りません。

```scala
  def kFold(nSamples: Int, nSplits: Int, seed: Long): Vector[Fold] =
    require(nSplits >= 1, "分割の数は 1 以上にしてください")
    require(nSamples >= nSplits, "件数は分割の数以上でなければなりません")
    val positions = Preprocessing.shuffle((0 until nSamples).toVector, seed)
    val sizes =
      (0 until nSplits).toVector.map(i =>
        nSamples / nSplits + (if i < nSamples % nSplits then 1 else 0)
      )
    sizes
      .scanLeft(0)(_ + _)
      .lazyZip(sizes)
      .map { (from, size) =>
        val test = positions.slice(from, from + size)
        Fold(positions.filterNot(test.toSet), test)
      }
      .toVector
```

- 並べ替えには第 2 章の `Preprocessing.shuffle`（`java.util.Random` の Fisher-Yates）を使います。Java 版の `Collections.shuffle(positions, new Random(seed))` と同じ乱数・同じ手順なので、**同じシードなら分け方まで一致します**
- 件数が割り切れないときは、余りを先頭の分割から 1 件ずつ配ります（`i < nSamples % nSplits`）
- 各分割の開始位置は `scanLeft` で件数を積み上げて求めます。Java 版は `from` 変数を書き換えながらループしました
- `positions.filterNot(test.toSet)` は「テストデータ以外」を並べ替えた順のまま残します。`Set` は `Int => Boolean` の関数でもあるので、そのまま述語として渡せます

### 分け方の性質をテストで固定する

具体的な行の並びではなく、分け方の **性質** をテストにします。

```scala
  test("割り切れない件数は余りを先頭の分割から 1 件ずつ配る") {
    val folds = CrossValidation.kFold(11, 4, 0)

    assert(folds.map(_.test.size) === Vector(3, 3, 3, 2))
  }

  test("テストデータは重ならず全体を覆う") {
    val folds = CrossValidation.kFold(10, 3, 0)

    assert(folds.flatMap(_.test).sorted === (0 until 10).toVector)
  }

  test("訓練データはテストデータ以外のすべての行") {
    val folds = CrossValidation.kFold(10, 3, 0)

    folds.foreach { fold =>
      assert(fold.train.size === 10 - fold.test.size)
      assert(fold.train.toSet.intersect(fold.test.toSet).isEmpty)
    }
  }
```

シードで分け方が決まることも確かめます。`kFold(10, 5, 0)` を 2 回呼べば同じ結果になり、シードを 1 に変えれば違う結果になります。`Fold` が case class なので、分け方どうしを `===` でそのまま比べられます。

## 11.8 評価関数を関数として渡す

### 交差検証の手順を 1 つの関数にする

交差検証の手順は「分割ごとに、新しいモデルを作り、訓練データで学習し、テストデータの予測を採点する」です。変わるのは **モデルの作り方** と **採点の仕方** だけなので、その 2 つを関数で受け取ります。

```scala
// src/main/scala/machinelearning/chapter11/Model.scala
/** 正解と予測のリストから 1 つのスコアを求める評価関数。T は正解ラベルの型。 */
type Metric[T] = (Vector[T], Vector[T]) => Double

/** 交差検証で学習と予測を繰り返すモデル。T は正解ラベルの型。 */
trait Model[T]:
  def fit(x: Vector[Features], t: Vector[T]): Model[T]
  def predict(x: Vector[Features]): Vector[T]
```

`Metric` は **型の別名** です。Java 版は `ToDoubleBiFunction<List<T>, List<T>>` を継承した関数型インターフェースを定義しなければなりませんでしたが、Scala では関数そのものが型なので、`type` の 1 行で名前を付けられます。Kotlin 版の `typealias` と同じ書き方で、F# 版の型の省略記法にも近い形です。

`Model` は第 10 章の `Classifier` と同じく、`fit` が学習した結果のモデルを返します。Java 版の `Model` は `void fit(…)` で自分の中の状態を書き換えました。値を返す形にすると、同じモデルを分割の数だけ使い回しても前の学習が残りません。

```scala
  def crossValidate[T](
      makeModel: () => Model[T],
      x: Vector[Features],
      t: Vector[T],
      folds: Vector[Fold],
      metric: Metric[T]
  ): LazyList[Double] =
    folds.to(LazyList).map { fold =>
      val fitted = makeModel().fit(pick(x, fold.train), pick(t, fold.train))
      metric(pick(t, fold.test), fitted.predict(pick(x, fold.test)))
    }

  private def pick[A](values: Vector[A], positions: Vector[Int]): Vector[A] =
    positions.map(values)
```

`pick` の `positions.map(values)` は、「位置のリストを `values` という関数に通す」と読みます。`Vector[A]` は `Int => A` の関数でもあるので、添字で取り出すラムダ式を書かずに済みます。

### 三角測量: 評価関数を差し替える

同じ分割・同じモデルで、評価関数だけを差し替えたときにスコアが変わることをテストにします。

```scala
  test("評価関数を差し替えると同じ分割で別のスコアになる") {
    val x = features(0.1, 0.2, 0.3, 0.4, 0.5, 0.6)
    val t = Vector("0", "1", "0", "1", "1", "1")
    val folds = CrossValidation.kFold(6, 3, 0)
    def run(metric: Metric[String]) =
      CrossValidation.crossValidate(() => DecisionTreeModel(1), x, t, folds, metric).toVector

    val accuracy = run(Metrics.accuracy)
    val recall = run(Metrics.classificationMetric(Metrics.recall, "1"))

    assert(accuracy !== recall)
  }
```

`Metrics.accuracy` は型引数を持つメソッドですが、`Metric[String]` が期待される場所ではイータ拡張で関数の値になり、型引数も `String` に決まります。Java 版のメソッド参照 `Metrics::accuracy` と同じ手軽さです。

### 必要な分だけ学習する: LazyList

`LazyList` は先頭から順に、必要になったときだけ計算します。分割ごとの学習が重いとき、最初の 1 件だけ見て打ち切れます。

```scala
  test("スコアは遅延評価で、取り出した分だけ学習する") {
    var fitted = 0
    val counting = new Model[String]:
      override def fit(x: Vector[Features], t: Vector[String]): Model[String] =
        fitted += 1
        this
      override def predict(x: Vector[Features]): Vector[String] = x.map(_ => "0")
    …
    val first = scores.head

    assert(fitted === 1)
    assert(first >= 0.0)
    assert(scores.toVector.size === 3)
    assert(fitted === 3)
  }
```

`scores.head` の時点では 1 回しか学習しておらず、すべて取り出した時点で 3 回になります。Java 版の `DoubleStream` も遅延評価ですが、**1 度しか使えない** ので、同じストリームから `head` を取ってからもう一度回すことはできません。`LazyList` は計算した要素を覚えている（メモ化する）ので、2 度目に回しても学習は 3 回のままです。Kotlin 版の `Sequence` は何度でも回せますが、そのたびに学習し直すので、ここは 3 つの言語で挙動が分かれるところです。

### 混同行列の指標を評価関数に変える

適合率・再現率・F 値は混同行列から求めるので、そのままでは `Metric` の形（正解と予測から採点する）になりません。正例を決めて包む高階関数を用意します。

```scala
  /** 混同行列から求める指標を、正例を決めて、正解と予測から求める評価関数に変える。 */
  def classificationMetric[A](score: ConfusionMatrix => Double, positive: A): Metric[A] =
    (actual, predicted) => score(ConfusionMatrix.of(actual, predicted, positive))
```

`Metrics.classificationMetric(Metrics.recall, "1")` と書けば、「`"1"` を正例とする再現率の評価関数」になります。Java 版は `ToDoubleFunction<ConfusionMatrix>` という別のインターフェースを引数に取りましたが、Scala では `ConfusionMatrix => Double` と書けば済みます。

## 11.9 Tribuo の評価器と突き合わせる

自作した指標と分割が、Tribuo の評価器と同じ結果になることを学習用テストで確かめます。予測には、第 3 章の `TribuoTrees.train` で学習した Tribuo の決定木（深さ 1）を使い、その予測から自作の指標と Tribuo の `LabelEvaluator` の両方で採点します。データは架空の 10 件です。

```scala
// src/test/scala/machinelearning/chapter11/TribuoEvaluationSpec.scala
class TribuoEvaluationSpec extends AnyFunSuite:
  import Samples.*

  private val positive = Label("1")
  private val x = features(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0)
  private val t = Vector("0", "0", "1", "0", "0", "1", "1", "0", "1", "1")
  private val model = TribuoTrees.train(x, t, 1)
  private val predicted = TribuoTrees.predict(model, x)
  private val evaluation = LabelEvaluator().evaluate(model, TribuoTrees.toDataset(x, t))

  test("混同行列が Tribuo の評価器と一致する") {
    val cm = ConfusionMatrix.of(t, predicted, "1")

    val tribuo = evaluation.getConfusionMatrix
    assert(
      Vector(tribuo.tp(positive), tribuo.fp(positive), tribuo.fn(positive), tribuo.tn(positive))
        === Vector(cm.tp.toDouble, cm.fp.toDouble, cm.fn.toDouble, cm.tn.toDouble)
    )
  }

  test("正解率と適合率と再現率と F 値が Tribuo の評価器と一致する") {
    val cm = ConfusionMatrix.of(t, predicted, "1")

    assert(Metrics.accuracy(t, predicted) === evaluation.accuracy() +- 1e-12)
    assert(Metrics.precision(cm) === evaluation.precision(positive) +- 1e-12)
    assert(Metrics.recall(cm) === evaluation.recall(positive) +- 1e-12)
    assert(Metrics.f1Score(cm) === evaluation.f1(positive) +- 1e-12)
  }
```

- この章にも `Model` があるので、Tribuo のモデルは `org.tribuo.Model[Label]` と **完全修飾名** で書きます。Java 版・Kotlin 版と同じ理由です
- Tribuo の混同行列の件数は `double` で返るので、自作の `Int` を `toDouble` でそろえます

分割の件数の配り方も突き合わせます。

```scala
  private def tribuoTestSizes(nSamples: Int, nSplits: Int): Vector[Int] =
    val xs = features((0 until nSamples).map(_.toDouble)*)
    val ts = (0 until nSamples).toVector.map(i => if i < nSamples / 2 then "0" else "1")
    val splitter = KFoldSplitter[Label](nSplits, 0L)
    val sizes = Vector.newBuilder[Int]
    splitter
      .split(TribuoTrees.toDataset(xs, ts), true)
      .forEachRemaining(fold => sizes += fold.test.size)
    sizes.result()

  test("分割ごとのテストデータの件数が Tribuo の KFoldSplitter と一致する") {
    Vector((10, 3), (7, 2), (11, 4)).foreach { (nSamples, nSplits) =>
      val mine = CrossValidation.kFold(nSamples, nSplits, 0).map(_.test.size)

      assert(mine === tribuoTestSizes(nSamples, nSplits), s"$nSamples 件を $nSplits 分割")
    }
  }
```

`KFoldSplitter.split` は Java の `Iterator` を返すので、`forEachRemaining` で件数を集めています。1 回分の分け方 `TrainTestFold` は `train`・`test` を **public なフィールド** で持つので、`fold.test.size` と読みます。`assert` の第 2 引数は、失敗したときに表示される手がかり（clue）です。ループの中のアサーションで、どの組み合わせで失敗したかが分かるようにしています。

同じ分割を渡したときに、交差検証の平均も一致することを確かめます。Tribuo の決定木を、この章の `Model` として使うアダプターをテスト側に用意します。

```scala
/** Tribuo の CART を、この章の Model として使うテスト用のアダプター。 */
case class TribuoTree(maxDepth: Int, model: Option[org.tribuo.Model[Label]] = None)
    extends Model[String]:
  override def fit(x: Vector[Features], t: Vector[String]): TribuoTree =
    copy(model = Some(TribuoTrees.train(x, t, maxDepth)))
  override def predict(x: Vector[Features]): Vector[String] =
    TribuoTrees.predict(model.getOrElse(throw IllegalStateException("学習していません")), x)

  test("同じ分割なら Tribuo の評価器で採点した正解率の平均と一致する") {
    val xs = features((1 to 20).map(_ * 0.05)*)
    val ts = (1 to 20).toVector.map(i => if i % 3 == 0 || i > 12 then "1" else "0")
    val folds = CrossValidation.kFold(20, 4, 0)

    val tribuo = folds.map { fold =>
      val trained = TribuoTrees.train(pick(xs, fold.train), pick(ts, fold.train), 1)
      val test = TribuoTrees.toDataset(pick(xs, fold.test), pick(ts, fold.test))
      LabelEvaluator().evaluate(trained, test).accuracy()
    }

    val mine =
      CrossValidation.crossValidate(() => TribuoTree(1), xs, ts, folds, Metrics.accuracy)

    assert(mine.sum / mine.size === tribuo.sum / tribuo.size +- 1e-12)
  }
```

回帰では、Tribuo の `RegressionEvaluator` に MSE が無いので、RMSE の 2 乗と比べます。

```scala
  test("MSE は Tribuo の RegressionEvaluator の RMSE の 2 乗と一致する") {
    val xr = features(1, 2, 3, 4, 5, 6)
    val tr = Vector(1.1, 2.3, 2.8, 4.4, 4.9, 6.2)
    val regression = TribuoRegression.train(SLMTrainer(true), xr, tr)

    val rmse = RegressionEvaluator()
      .evaluate(regression, TribuoRegression.toDataset(xr, tr))
      .rmse()
      .values()
      .iterator()
      .next()

    assert(
      Metrics.meanSquaredError(tr, TribuoRegression.predict(regression, xr)) === rmse * rmse +- 1e-9
    )
  }
```

突き合わせで確かめた Tribuo の約束事は、Kotlin 版（[ADR 002](../../../adr/002-kotlin-ml-libraries.md)）・Java 版（[ADR 005](../../../adr/005-java-ml-libraries.md)）と同じでした。

- `LabelEvaluator` の評価結果（`LabelEvaluation`）は、ラベルを指定して `precision(Label)`・`recall(Label)`・`f1(Label)` を返す。どのラベルを正例とするかを呼ぶたびに指定する設計で、自作の `positive` と同じ考え方
- 正例を一度も予測しない場合、Tribuo も適合率・再現率・F 値を 0.0 にする。`NaN` にはならない
- `RegressionEvaluator` には MSE が無く、RMSE の 2 乗が自作の MSE と一致する（MAE・RMSE・R² が第 7 章の自作と一致することは第 7 章で確かめました）
- `KFoldSplitter` と自作の `kFold` は、テストデータの件数の配り方（余りを先頭から配る）が、試した 3 通りで一致した。並べ替えの乱数は別の実装なので、同じシードでも行の割り当ては一致しない
- Tribuo の `CrossValidation` は分割を自分で作るので、分割を渡すことはできない。同じ分割で比べるときは、分割ごとに学習して `LabelEvaluator` で採点する
- 追加の依存は要りませんでした。`LabelEvaluator` は `tribuo-classification-tree`（第 3 章で入れた）の依存に、`KFoldSplitter` は Tribuo の中核に、`RegressionEvaluator` は `tribuo-regression-slm`（第 7 章で入れた）の依存に含まれています

## 11.10 実データで評価する

### 簡略化した前処理

交差検証にかけるには、欠損値や文字列の列を数値にしておく必要があります。前処理パイプラインは第 8 章で詳しく扱うので、この章ではそれを簡略化したものを `Dataset` に置きます。分割の前に全体の平均値で補完する、割り切った形です。

```scala
// src/main/scala/machinelearning/chapter11/Experiments.scala
case class Dataset[T](x: Vector[Features], t: Vector[T])

object Dataset:
  /** 客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、Survived の文字列を正解ラベルにする。年齢の欠損値は平均値で補う。 */
  def prepareSurvived(table: Table): Dataset[String] =
    val ageMean = Preprocessing.columnMeans(table.rows, Vector(Age))(Age)
    val x = table.rows.map(row =>
      Features(
        SurvivedFeatures,
        Vector(
          number(row, "Pclass"),
          row.number(Age).getOrElse(ageMean),
          if row.text("Sex") == "male" then 1.0 else 0.0
        )
      )
    )
    Dataset(x, table.rows.map(_.text("Survived")))
```

`row.number(Age)` は `Option[Double]` を返すので、`getOrElse(ageMean)` で補完します。Java 版の `Optional.orElse` と同じ形ですが、第 2 章から `Option` を使い続けているので、この章で新しく覚えることはありません。

### 交差検証の実験

指標の名前と評価関数を、表示する順に並べます。Survived では `"1"`（生存）を正例にします。

```scala
  /** Survived の評価指標。表示する順に並べる。 */
  val SurvivedMetrics: Vector[(String, Metric[String])] = Vector(
    "正解率" -> Metrics.accuracy,
    "適合率" -> Metrics.classificationMetric(Metrics.precision, Survived),
    "再現率" -> Metrics.classificationMetric(Metrics.recall, Survived),
    "F値" -> Metrics.classificationMetric(Metrics.f1Score, Survived)
  )

  /** cinema の評価指標。第 7 章の RMSE・MAE をそのまま関数として渡す。 */
  val CinemaMetrics: Vector[(String, Metric[Double])] = Vector(
    "RMSE" -> RegressionMetrics.rootMeanSquaredError,
    "MAE" -> RegressionMetrics.meanAbsoluteError
  )
```

Java 版は「順序を保つ `Map`」を作るために `LinkedHashMap` に順に入れてから `unmodifiableMap` で包みました。Scala 版は、順序に意味があるので `Map` ではなく `Vector[(String, Metric[T])]` にしています。第 9 章で `SeqMap` と `Vector[(K, V)]` を使い分けたのと同じ判断です。第 7 章の `RegressionMetrics.rootMeanSquaredError` は `(Vector[Double], Vector[Double]) => Double` なので、`Metric[Double]` としてそのまま渡せます。

```scala
  /** 同じ分割で、評価指標ごとに交差検証のスコアの平均を求める。 */
  def evaluate[T](
      makeModel: () => Model[T],
      data: Dataset[T],
      metrics: Vector[(String, Metric[T])]
  ): Vector[(String, Double)] =
    val folds = CrossValidation.kFold(data.x.size, NSplits, Seed)
    metrics.map { (name, metric) =>
      val scores = CrossValidation.crossValidate(makeModel, data.x, data.t, folds, metric)
      name -> scores.sum / scores.size
    }
```

- 分割（`folds`）は 1 回だけ作り、すべての指標で同じ分割を使います。指標によって分け方が違うと、指標どうしを比べられないからです
- 型引数 `T` によって、`Model[String]` と回帰の `Metric[Double]` を組み合わせるような取り違えはコンパイルエラーになります
- 第 3 章の決定木と第 7 章の線形回帰は、`Model` を実装するアダプター（`DecisionTreeModel`・`LinearRegressionModel`）で包みます。どちらも case class で、`fit` が `copy` で学習済みの値を返します

`Main` は、交差検証の平均を表示するだけです。

```scala
// src/main/scala/machinelearning/chapter11/Main.scala
object Main:

  def run(print: String => Unit): Unit =
    val dataDir = DataDir.current()
    print(s"Survived（決定木、${Experiments.NSplits} 分割交差検証の平均）")
    show(print, Experiments.evaluateSurvived(Paths.get(dataDir, "Survived.csv")), 4)
    print(s"cinema（線形回帰、${Experiments.NSplits} 分割交差検証の平均）")
    show(print, Experiments.evaluateCinema(Paths.get(dataDir, "cinema.csv")), 2)

  private def show(print: String => Unit, scores: Vector[(String, Double)], decimals: Int): Unit =
    scores.foreach((name, score) =>
      print(s"  $name: ${String.format(Locale.ROOT, s"%.${decimals}f", score)}")
    )
```

```console
$ sbt "run chapter11"
Survived（決定木、5 分割交差検証の平均）
  正解率: 0.7811
  適合率: 0.7759
  再現率: 0.6306
  F値: 0.6833
cinema（線形回帰、5 分割交差検証の平均）
  RMSE: 405.77
  MAE: 321.53
```

Survived の決定木は、正解率 0.7811 に対して再現率が 0.6306 です。生存者のうち 4 割近くを見逃していることが、正解率だけでは見えませんでした。適合率 0.7759 は、「生存」と予測した人の 8 割近くが本当に生存していたことを表します。

cinema の RMSE（405.77）は MAE（321.53）より大きく、11.6 節で見たとおり、大きく外れた予測があることを示します。第 7 章では 1 回の分割で RMSE が 376.14 でしたが、この章は外れ値を除かずに 5 回の平均を取っているので、単純には比べられません。

**これら 6 つの値は、[Java 版の第 11 章](../java/11-evaluation-metrics-and-cross-validation.md) の実測値と完全に一致しました。** 分割に第 2 章の `Preprocessing.shuffle`（`java.util.Random` の Fisher-Yates）を使っていて、Java 版の `Collections.shuffle` と同じ乱数・同じ手順だからです。Kotlin 版は `kotlin.random.Random` なので、同じシード 0 でも行の割り当てが違い、値も違います（Kotlin 版の正解率は 0.7677）。交差検証の平均でも、分け方による違いは残ります。

### 実データのテスト

実データのテストは、学習データが無ければスキップします。Survived の交差検証が、同じ分割で Tribuo の決定木と `LabelEvaluator` で採点した平均と一致することと、`Main` の出力を固定します。

```scala
// src/test/scala/machinelearning/chapter11/EvaluationDataSpec.scala
  test("同じ分割なら Tribuo の評価器で採点した Survived の平均と一致する") {
    requireData(): Unit
    val data = Dataset.prepareSurvived(Table.load(survived))
    val folds = CrossValidation.kFold(data.x.size, Experiments.NSplits, Experiments.Seed)
    val positive = Label("1")

    val evaluations = folds.map { fold =>
      val model = TribuoTrees.train(pick(data.x, fold.train), pick(data.t, fold.train), 2)
      val test = TribuoTrees.toDataset(pick(data.x, fold.test), pick(data.t, fold.test))
      LabelEvaluator().evaluate(model, test)
    }
    def mean(score: LabelEvaluation => Double): Double =
      evaluations.map(score).sum / evaluations.size

    val scores = Experiments.evaluate(() => TribuoTree(2), data, Experiments.SurvivedMetrics).toMap

    assert(scores("正解率") === mean(_.accuracy()) +- 1e-12)
    assert(scores("適合率") === mean(_.precision(positive)) +- 1e-12)
    assert(scores("再現率") === mean(_.recall(positive)) +- 1e-12)
    assert(scores("F値") === mean(_.f1(positive)) +- 1e-12)
  }
```

`LabelEvaluation` のどのメソッドで採点するかを `LabelEvaluation => Double` で受け取る `mean` を作り、4 つの指標の平均を同じ形で書いています。ここでも、変わる部分だけを関数で渡しています。`assume` は `Assertion` を返すので、`-Wvalue-discard` の設定では `requireData(): Unit` と型を書いて捨てることを明示します（第 2 章以降と同じです）。

## 11.11 品質チェック

`sbt "scalafmtAll; scalafmtCheckAll; test"` で、整形・コンパイル・テストをまとめて実行しました。この章の実装で `-Xfatal-warnings` に止められた箇所はありません。

```text
EvaluationDataSpec:
- Survived の特徴量は 3 列で、年齢の欠損値が平均値で補われる
- 同じ分割なら Tribuo の評価器で採点した Survived の平均と一致する
- 実行すると交差検証の平均を表示する
MetricsSpec:
- すべて正解なら真陽性と真陰性だけを数える
- 外れた予測を偽陽性と偽陰性に分けて数える
- 正例を入れ替えると真陽性と真陰性が入れ替わる
- 正解と予測の件数が違えばエラーになる
- 適合率は正例と予測したうち本当に正例だった割合
- 再現率は本当の正例のうち正例と予測できた割合
- F 値は適合率と再現率の調和平均
- 正例を 1 件も予測しなければ適合率も再現率も F 値も 0 になる
- 正解率は一致した割合
- 平均二乗誤差は誤差の 2 乗の平均
- 平均二乗誤差は外れた予測に敏感で平均絶対誤差より大きく増える
- 混同行列の指標を評価関数に変える
CrossValidationSpec:
- 割り切れる件数は同じ大きさのテストデータに分ける
- 割り切れない件数は余りを先頭の分割から 1 件ずつ配る
- テストデータは重ならず全体を覆う
- 訓練データはテストデータ以外のすべての行
- 同じシードなら同じ分け方になる
- シードが違えば分け方が変わる
- 件数より多い分割はできない
- 分割ごとに学習し直して評価関数で採点する
- 評価関数を差し替えると同じ分割で別のスコアになる
- スコアは遅延評価で、取り出した分だけ学習する
- 回帰でも同じ交差検証の手順を使える
TribuoEvaluationSpec:
- 混同行列が Tribuo の評価器と一致する
- 正解率と適合率と再現率と F 値が Tribuo の評価器と一致する
- 正例を 1 件も予測しなければ Tribuo の評価器も適合率と再現率と F 値を 0 にする
- MSE は Tribuo の RegressionEvaluator の RMSE の 2 乗と一致する
- 分割ごとのテストデータの件数が Tribuo の KFoldSplitter と一致する
- 同じ分割なら Tribuo の評価器で採点した正解率の平均と一致する
```

学習データが無い環境（`ML_DATA_DIR=/nonexistent sbt test`）では、`EvaluationDataSpec` の 3 件が canceled になり、ビルドは成功します。

## 11.12 可視化について

Scala 版では Notebook と可視化の節を設けません。混同行列のヒートマップ、分割ごとのスコアのばらつきのグラフは、[Python 版の第 11 章](../python/11-evaluation-metrics-and-cross-validation.md) と [Kotlin 版の第 11 章](../kotlin/11-evaluation-metrics-and-cross-validation.md) の「Notebook による探索と可視化」の節を参照してください。

## 11.13 まとめ

この章では、評価指標と K 分割交差検証を Scala の TDD で自作し、Tribuo の評価器と突き合わせました。

| 作ったもの | 内容 | 突き合わせた相手 |
|-----------|------|----------------|
| `ConfusionMatrix` | 4 つの件数を持つ case class。正例を選べる | Tribuo の `LabelEvaluator` の混同行列 |
| `Metrics` | 適合率・再現率・F 値・MSE・正解率 | `LabelEvaluator`・`RegressionEvaluator` |
| `Metric[T]` | 評価関数の型の別名。混同行列の指標も包んで渡せる | — |
| `CrossValidation.kFold` | シード付きの K 分割 | Tribuo の `KFoldSplitter`（件数の配り方） |
| `CrossValidation.crossValidate` | 遅延評価の交差検証 | 分割ごとに `LabelEvaluator` で採点した平均 |

Scala 版ならではの学びです。

1. **関数型は `type` だけで名前が付く** — Java 版は `ToDoubleBiFunction` を継承した関数型インターフェースを定義した。Scala は `type Metric[T] = (Vector[T], Vector[T]) => Double` の 1 行で済み、メソッドはイータ拡張でそのまま渡せる
2. **`copy` と `foldLeft` で数える** — 4 つの `int` を書き換える代わりに、case class の `copy` で 1 つだけ増やし、タプルのパターンマッチで 4 つの場合を並べた。場合分けの網羅が形から読める
3. **`LazyList` はメモ化する** — Java の `DoubleStream` は 1 度しか使えず、Kotlin の `Sequence` は回すたびに計算し直す。`LazyList` は取り出した分だけ計算して覚えるので、`head` を見てから全体を取り出しても学習は分割の数のまま
4. **モデルを値として返す** — `fit` が新しいモデルを返す形にしたので、同じモデルを分割の数だけ使い回しても前の学習が残らない。Java 版の `void fit` は自分の中の状態を書き換えていた
5. **数値は Java 版と完全に一致した** — 分割に `java.util.Random` の Fisher-Yates を使っているので、交差検証の平均まで Java 版と同じになった

次の章では、正則化で過学習を抑え、検証データでモデルを選ぶ方法を実装します。
