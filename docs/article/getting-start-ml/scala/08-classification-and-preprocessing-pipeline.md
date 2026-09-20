---
type: Article
title: "第 8 章: 実践的な分類と前処理パイプライン"
description: "Survived データのグループ別中央値・最頻値の補完とダミー変数化を sealed trait の前処理にまとめ、クラスの重みを付けた決定木・モデルの保存と読み込みを Scala 3 で TDD で実装し、Java 版・Kotlin 版・Tribuo の CART と突き合わせる。"
tags: [article,getting-start-ml,scala]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T03:10:45Z }
---

# 第 8 章: 実践的な分類と前処理パイプライン

## 8.1 はじめに

これまでの章のデータは、欠損値が少なく、特徴量もすべて数値でした。現実のデータはそうはいきません。この章で扱うタイタニック号の乗客データには、次の 3 つの難しさがあります。

- **欠損値が多い** — 年齢が 2 割近く欠けている
- **カテゴリ値がある** — 性別や乗船した港が文字列で記録されている
- **クラスが偏っている** — 生存者より死亡者のほうが多い

この章では、これらを 1 つずつ小さな部品として TDD で実装し、前処理からモデルまでを 1 つの **パイプライン** につなぎます。最後にそのパイプラインをファイルに保存し、読み込んで予測できるようにします。保存したモデルは、第 15 章で作る機械学習 API から使います。

[Python 版の第 8 章](../python/08-classification-and-preprocessing-pipeline.md) は、scikit-learn の変換器・`Pipeline`・`DecisionTreeClassifier` の `class_weight` を使いました。Scala 版には、次の 3 つの違いがあります。

- **前処理を ADT（代数的データ型）で表す** — 学習済みの前処理を `sealed trait FittedTransformer` と case class で表し、「`fit` する前は `transform` できない」ことを型で保証する。使うときだけ `transform` を関数の値として取り出し、`andThen` で合成する
- **クラスの重みを付けた決定木を自作する** — Tribuo の決定木にはクラスの重み付けが無い（[ADR 002](../../../adr/002-kotlin-ml-libraries.md)）ので、第 3 章の決定木を重み付きに書き直す。Tribuo とは重み付けなしで突き合わせる
- **モデルはテキストで保存する** — Java 版・Kotlin 版は Java のシリアライズで保存しましたが、Scala の不変コレクションはそのままでは安全に読み戻せませんでした（8.10 節）。ADT をタブ区切りのテキストに書き出す形に変えます

実装は [Java 版の第 8 章](../java/08-classification-and-preprocessing-pipeline.md)・[Kotlin 版の第 8 章](../kotlin/08-classification-and-preprocessing-pipeline.md) と同じ乱数・同じ手順で分割するので、正解率や見つけた生存者の数も Java 版と一致するはずです。8.12 節でそれを確かめます。

## 8.2 題材とデータ

### Survived.csv

`apps/data/sukkiri-ml/Survived.csv` は、タイタニック号の乗客 891 人の記録です。列は次のとおりです。

| 列 | 意味 | 型 |
|----|------|----|
| PassengerId | 乗客の番号 | 数値 |
| Survived | 生存（1）か死亡（0）か | 数値（正解ラベル） |
| Pclass | 客室のクラス（1・2・3） | 数値 |
| Sex | 性別（male・female） | 文字列 |
| Age | 年齢 | 数値（欠損あり） |
| SibSp | 同乗した兄弟・配偶者の数 | 数値 |
| Parch | 同乗した親・子の数 | 数値 |
| Ticket | 切符の番号 | 文字列 |
| Fare | 運賃 | 数値 |
| Cabin | 客室の番号 | 文字列（欠損が多い） |
| Embarked | 乗船した港（C・Q・S） | 文字列（欠損あり） |

### 使う特徴量と、使わない列

`PassengerId`・`Ticket`・`Cabin` は使いません。番号は生存と関係がなく、`Cabin` は 891 人中 687 人で欠けているからです。使うのは 7 列です。

```scala
val FeatureColumns: Vector[String] =
  Vector("Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked")
```

### 年齢はグループごとの中央値で補完する

年齢は 177 件欠けています。全体の中央値で埋めることもできますが、客室のクラスと性別によって年齢の分布は違います。そこで「同じ `Pclass` と `Sex` の乗客の中央値」で補完します。第 2 章の「訓練データで求めた値でテストデータも補完する」という約束は、この章でも守ります。

## 8.3 TODO リストの作成

```text
- [ ] Survived.csv を読み込み、件数と欠損値の数を確かめる
- [ ] 年齢を Pclass と Sex のグループごとの中央値で補完する
- [ ] 乗船した港を最頻値で補完する
- [ ] Sex と Embarked をダミー変数にする
- [ ] クラスの重みを付けられる決定木を作る
- [ ] 前処理とモデルをパイプラインにつなぐ
- [ ] 学習済みのパイプラインを保存して読み込む
- [ ] 正解率と、見つけた生存者の数で評価する
- [ ] 実データでクラスの重みの効果を確かめる
```

## 8.4 CSV を読み込む

読み込みは第 2 章の `Table` と `Row` をそのまま使います。`Row` はセルを文字列の `Map` で持ち、`number` で数値（空欄なら `None`）を、`text` で文字列を返します。この章で初めて `text` が主役になります。性別や港は文字列のままで補完・ダミー変数化するからです。

```scala
case class Row(cells: Map[String, String]):
  def number(column: String): Option[Double] =
    val cell = text(column)
    if cell.trim.isEmpty then None else Some(cell.toDouble)

  def text(column: String): String =
    cells.getOrElse(column, throw IllegalArgumentException(s"列がありません: $column"))

  def isMissing(column: String): Boolean = text(column).trim.isEmpty
```

Kotlin 版は Kotlin DataFrame に読み込ませたため、`Sex` の 1 文字の値が `Char` に推定されたり、すべて欠けた列が `Nothing?` になったりする Red が生まれました。Scala 版は Java 版と同じく「セルは文字列」と決めているので、その種の Red は出ません。代わりに、数値にしたいときに `number` を呼び、文字列のままでよいときに `text` を呼ぶ、という選択が呼び出し側に残ります。

特徴量の列と正解ラベルの取り出しは、この章専用の小さな object にまとめます。

```scala
object SurvivedData:
  val FeatureColumns: Vector[String] =
    Vector("Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked")

  val Target = "Survived"

  /** 行を、特徴量の列だけを並べた表にする。行がほかの列を持っていても使わない。 */
  def features(rows: Vector[Row]): Table = Table(FeatureColumns, rows)

  /** 行の Survived 列を、整数の正解ラベルにする。 */
  def target(rows: Vector[Row]): Vector[Int] = rows.map(_.text(Target).toInt)
```

`Table` は「列名の並び」と「行」を別々に持つので、行が `Ticket` や `Cabin` を持っていても、列の並びを 7 列にするだけで使わない列を落とせます。行を作り直す必要はありません。

テストは架空の 1 行で書きます。

```scala
test("CSV の行から特徴量の列の表と Survived 列の正解ラベルを作る") {
  val csv = Tables.table(
    "PassengerId,Survived,Pclass,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked",
    "1,1,2,female,28,0,1,X-2,15,,C"
  )

  assert(
    SurvivedData.features(csv.rows).columns
      === Vector("Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked")
  )
  assert(SurvivedData.target(csv.rows) === Vector(1))
}
```

## 8.5 年齢をグループごとの中央値で補完する

### 前処理の部品を trait で定義する

前処理には「訓練データから値を学ぶ」段階と「その値でデータを変換する」段階があります。scikit-learn の `fit` と `transform` です。この 2 つを別々の型にすると、「学習する前に変換してしまう」間違いがコンパイルエラーになります。

```scala
/** 訓練データから変換に必要な値を求める前処理。 */
trait Transformer:
  def fit(x: Table): FittedTransformer

/** fit で求めた値を使ってデータを変換する前処理。 */
sealed trait FittedTransformer:
  def transform(x: Table): Table
```

Scala なら `FittedTransformer` を `Table => Table` という関数の型にもできます。実際、合成（8.9 節）は関数のほうが書きやすい。それでも `sealed trait` にしたのは、次の 2 つの理由からです。

1. **どんな前処理があるかをコンパイラが数え上げられる** — 保存（8.10 節）のように「前処理の種類ごとに違う処理」を書くとき、`match` の網羅性をコンパイラが検査してくれる
2. **学習した値が中身として残る** — 関数にしてしまうと、求めた中央値や最頻値が関数の中に閉じ込められ、外から確かめることも、ファイルに書き出すこともできない

関数として合成したいときは、`transformer.transform` と書けば `Table => Table` の値がその場で得られます。Scala では「データは ADT、振る舞いは関数」を両取りできるので、どちらかを選ぶ必要はありません。

### Red: 最初のテスト

まず、同じグループの中央値で補完するテストを書きます。

```scala
private val ageImputer = GroupMedianImputer("Age", Vector("Pclass", "Sex"))

test("GroupMedianImputer は同じグループの中央値で欠損値を補完する") {
  val x = table("Pclass,Sex,Age", "1,female,20", "1,female,30", "1,female,70", "1,female,")

  val filled = ageImputer.fit(x).transform(x)

  assert(numbers(filled, "Age") === Vector(20.0, 30.0, 70.0, 30.0))
}
```

`GroupMedianImputer` がまだ無いのでコンパイルが通りません。これが Red です。

### Green: 仮実装

最初は「欠けていない値の中央値で埋める」だけの実装にします。グループは見ません。

```scala
case class GroupMedianImputer(column: String, by: Vector[String]) extends Transformer:
  override def fit(x: Table): FittedTransformer =
    val known = x.rows.filterNot(_.isMissing(column))
    FittedGroupMedianImputer(
      column,
      by,
      Map.empty,
      GroupMedianImputer.median(known.map(_.number(column).get))
    )
```

このテストは通ります。3 人とも同じグループなので、全体の中央値とグループの中央値が同じだからです。

### 三角測量: グループごとに異なる中央値

グループを 2 つに分けたテストを足すと、仮実装が壊れます。

```scala
test("GroupMedianImputer はグループごとに異なる中央値で補完する") {
  val x = table(
    "Pclass,Sex,Age",
    "1,female,40",
    "1,female,50",
    "1,female,",
    "3,male,10",
    "3,male,20",
    "3,male,"
  )

  val filled = ageImputer.fit(x).transform(x)

  assert(numbers(filled, "Age") === Vector(40.0, 50.0, 45.0, 10.0, 20.0, 15.0))
}
```

全体の中央値（30.0）で埋めてしまうので失敗します。グループごとに中央値を求める実装に進みます。

```scala
case class GroupMedianImputer(column: String, by: Vector[String]) extends Transformer:
  override def fit(x: Table): FittedTransformer =
    val known = x.rows.filterNot(_.isMissing(column))
    val groups = known.groupMap(GroupMedianImputer.groupOf(_, by))(_.number(column).get)
    FittedGroupMedianImputer(
      column,
      by,
      groups.view.mapValues(GroupMedianImputer.median).toMap,
      GroupMedianImputer.median(known.map(_.number(column).get))
    )

object GroupMedianImputer:
  /** 行のグループ。by の列の値を並べた Vector で、Map のキーに使う。 */
  def groupOf(row: Row, by: Vector[String]): Vector[String] = by.map(row.text)

  /** 中央値。件数が偶数なら中央の 2 つの平均。 */
  def median(values: Vector[Double]): Double =
    val sorted = values.sorted
    val middle = sorted.size / 2
    if sorted.size % 2 == 1 then sorted(middle) else (sorted(middle - 1) + sorted(middle)) / 2
```

ここで Scala の不変コレクションが効いてきます。グループのキーは `Vector("1", "female")` のような値の並びで、`Vector` の等価判定は中身の比較なので、そのまま `Map` のキーにできます。Java 版は `List<String>` を、C# 版は配列を包む型を用意しました。Scala は `groupMap` 1 つでグループ分けと値の取り出しが同時にでき、`view.mapValues(...).toMap` で値だけを中央値に変換できます。

### 訓練データで求めた値を別のデータに使う

`fit` と `transform` を分けた理由を、テストで示します。

```scala
test("GroupMedianImputer は訓練データで求めた中央値を別のデータの補完に使う") {
  val train = table("Pclass,Sex,Age", "2,male,30", "2,male,34")
  val other = table("Pclass,Sex,Age", "2,male,")

  val filled = ageImputer.fit(train).transform(other)

  assert(numbers(filled, "Age") === Vector(32.0))
}
```

補完に使った値は `FittedGroupMedianImputer` の中に残っているので、テストデータにも、後から来る 1 人の乗客にも、同じ値を使えます。

### 訓練データに無いグループと、全部欠けたグループ

2 等客室の女性が訓練データに 1 人もいなければ、そのグループの中央値はありません。全体の中央値で補完します。

```scala
private def fill(row: Row): Row =
  if !row.isMissing(column) then row
  else
    val median = medians.getOrElse(GroupMedianImputer.groupOf(row, by), overallMedian)
    Rows.updated(row, column, median.toString)
```

`Map.getOrElse` を使うだけで、Kotlin 版で `Nothing?` の型推定に悩まされた「年齢がすべて欠けたグループ」も同じ道を通ります。`fit` のときに欠けている行を先に落としているので、そのグループは `medians` に現れず、自然に全体の中央値になります。

補完した値は `Rows.updated` で新しい行にします。元の行は変えません。

```scala
private[chapter08] object Rows:
  def updated(row: Row, column: String, value: String): Row =
    Row(row.cells.updated(column, value))
```

`Map.updated` が新しい `Map` を返すので、「元を変更しない」ことを守るために防御的コピーを書く必要はありません。Java 版は `new HashMap<>(row.cells())` と書きました。

```scala
test("GroupMedianImputer は元の表を変更しない") {
  val x = table("Pclass,Sex,Age", "1,male,30", "1,male,")

  val _ = ageImputer.fit(x).transform(x)

  assert(x.rows(1).isMissing("Age"))
}
```

`val _ =` は「戻り値を捨てる」と明示する書き方です。Scala 版のビルドは `-Wvalue-discard -Xfatal-warnings` を付けているので、値を黙って捨てるとコンパイルエラーになります。捨てるつもりであることをコードに残せるので、この章のように「副作用を期待しない」テストでは、かえって意図がはっきりします。

## 8.6 乗船した港を最頻値で補完する

港は文字列なので、中央値ではなく最頻値（最も多い値）で補完します。

```scala
test("MostFrequentImputer は訓練データで最も多い値で欠損値を補完する") {
  val train = table("Embarked", "S", "C", "S", "")
  val other = table("Embarked", "", "Q")

  val filled = embarkedImputer.fit(train).transform(other)

  assert(texts(filled, "Embarked") === Vector("S", "Q"))
}
```

```scala
case class MostFrequentImputer(column: String) extends Transformer:
  override def fit(x: Table): FittedTransformer =
    val counts = x.rows.filterNot(_.isMissing(column)).groupMapReduce(_.text(column))(_ => 1)(_ + _)
    // maxBy は最初の最大値を返すので、同数なら値の順で前のものを選ぶ
    FittedMostFrequentImputer(column, counts.toVector.sortBy(_._1).maxBy(_._2)._1)
```

`groupMapReduce` は「グループ分け・値の変換・畳み込み」を 1 回の走査で行う Scala の標準メソッドです。Java 版は `Collectors.groupingBy` と `Map.merge` で同じことを書きました。

同数のときにどれを選ぶかは決めておく必要があります。Java 版は `LinkedHashMap` に入れて「先に現れた値」を選びました。Scala の `Map` は順序を保たないので、`sortBy(_._1)` で値の順に並べてから `maxBy` します。同数なら辞書順で前の値になります。Survived.csv の `Embarked` は `S` が明らかに多いので、この違いが結果を変えることはありません。

## 8.7 カテゴリ値をダミー変数にする

決定木は数値しか見ないので、`Sex` と `Embarked` を 0 と 1 の列に変えます。カテゴリが n 個なら n-1 列にします（最初のカテゴリは、ほかの列がすべて 0 であることで表せます）。

### Red → Green: 素直な実装

```scala
test("DummyEncoder は 2 値のカテゴリを、最初のカテゴリを除いた 0 と 1 の列にする") {
  val x = table("Pclass,Sex", "1,female", "3,male", "2,male")

  val encoded = DummyEncoder(Vector("Sex")).fit(x).transform(x)

  assert(encoded.columns === Vector("Pclass", "Sex_male"))
  assert(numbers(encoded, "Sex_male") === Vector(0.0, 1.0, 1.0))
}
```

```scala
case class DummyEncoder(columns: Vector[String]) extends Transformer:
  override def fit(x: Table): FittedTransformer =
    FittedDummyEncoder(columns.map(column => column -> DummyEncoder.categoriesOf(x, column).tail))

object DummyEncoder:
  private def categoriesOf(x: Table, column: String): Vector[String] =
    x.rows.filterNot(_.isMissing(column)).map(_.text(column)).distinct.sorted
```

`distinct.sorted` で「重複なく並べ替えたカテゴリ」を作り、`.tail` で最初のカテゴリを落とします。Java 版の `subList(1, size)` に当たりますが、`Vector.tail` は部分ビューではなく独立した `Vector` を返すので、Java 版が保存のときに踏んだ「部分リストがシリアライズできない」問題は起きません。

### 三角測量: 別のデータにも同じ列を作る

テストデータに `C` の乗客がいなくても、訓練データと同じ列を作らなければモデルに渡せません。

```scala
test("DummyEncoder は別のデータにも訓練データと同じ列を作る") {
  val train = table("Embarked", "C", "Q", "S")
  val other = table("Embarked", "S", "S")

  val encoded = DummyEncoder(Vector("Embarked")).fit(train).transform(other)

  assert(encoded.columns === Vector("Embarked_Q", "Embarked_S"))
  assert(numbers(encoded, "Embarked_Q") === Vector(0.0, 0.0))
  assert(numbers(encoded, "Embarked_S") === Vector(1.0, 1.0))
}
```

学習済みの側は、列名とカテゴリの組を **順序を保つ形** で持つ必要があります。Scala の `Map` は順序を保たないので、`Vector[(String, Vector[String])]` にします。Java 版は `LinkedHashMap` を使いましたが、`Map.copyOf` が順序を保たないので `LinkedHashMap` に写し直す注意書きが要りました。組の `Vector` なら、順序は型の意味そのものです。

```scala
case class FittedDummyEncoder(dummies: Vector[(String, Vector[String])]) extends FittedTransformer:
  override def transform(x: Table): Table =
    val columns = dummies.foldLeft(x.columns) { case (columns, (column, categories)) =>
      columns.filterNot(_ == column) ++ categories.map(category => s"${column}_$category")
    }
    Table(columns, x.rows.map(encode))

  private def encode(row: Row): Row =
    dummies.foldLeft(row) { case (encoded, (column, categories)) =>
      val value = row.text(column)
      categories.foldLeft(encoded) { (encoded, category) =>
        Rows.updated(encoded, s"${column}_$category", if value == category then "1" else "0")
      }
    }
```

列の並べ替えも行の書き換えも `foldLeft` です。「前の状態に 1 つずつ変更を重ねて新しい値を作る」形は、変更できるコレクションを使わない Scala ではよく出てきます。F# 版の `List.fold` と同じ考え方です。

## 8.8 クラスの重みを付けた決定木を作る

### なぜ自作するのか

Survived.csv は死亡 549 人・生存 342 人と偏っています。普通に学習すると、決定木は「迷ったら死亡」と予測しがちで、助かった人を見落とします。scikit-learn には `class_weight="balanced"` がありますが、Tribuo の CART にはクラスの重みを渡す口がありません（[ADR 002](../../../adr/002-kotlin-ml-libraries.md)）。そこで、第 3 章で作った決定木を「1 件ごとの重み」を通す形に書き直します。

第 3 章との違いは 2 つだけです。

- 正解ラベルが `String` ではなく `Int`（生存 1・死亡 0）
- 件数の代わりに **重みの合計** で不純度と多数決を計算する

分割の型は第 3 章の `Split` をそのまま使います。木は、ラベルが `Int` になるので、この章用の `enum` を定義します。

```scala
enum Tree:
  case Leaf(label: Int)
  case Node(split: Split, left: Tree, right: Tree)
```

Java 版は `sealed interface TreeNode permits LeafNode, SplitNode` と 2 つの record を別ファイルに書きました。Scala 3 の `enum` なら 3 行です。F# 版の判別共用体に当たります。

### 重み付きのジニ不純度

第 3 章のジニ不純度は「ラベルごとの件数の割合」から求めました。重み付きでは、件数を重みの合計に置き換えます。

```scala
test("重みがすべて 1 なら第 3 章のジニ不純度と同じ値になる") {
  val labels = Vector(0, 1, 1)

  assert(
    WeightedTrees.weightedGini(labels, Vector(1.0, 1.0, 1.0))
      === DecisionTrees.gini(labels.map(_.toString))
  )
}

test("重みの大きいラベルほど多いものとして不純度を計算する") {
  assert(WeightedTrees.weightedGini(Vector(0, 1), Vector(1.0, 3.0)) === 0.375 +- 1e-12)
}
```

1 つ目のテストは、第 3 章の実装を「正解」として使う突き合わせです。新しい実装が古い実装を一般化していることを、テストで固定できます。

```scala
def weightedGini(labels: Vector[Int], weights: Vector[Double]): Double =
  val total = weights.sum
  1.0 - weightSums(labels, weights).values.map(weight => math.pow(weight / total, 2)).sum

private def weightSums(labels: Vector[Int], weights: Vector[Double]): Map[Int, Double] =
  labels.zip(weights).foldLeft(ListMap.empty[Int, Double]) { case (sums, (label, weight)) =>
    sums.updated(label, sums.getOrElse(label, 0.0) + weight)
  }
```

`ListMap` は挿入順を保つ不変の `Map` です。多数決で同点になったときに「先に現れたラベル」を選ぶために使います。Java 版の `LinkedHashMap` に当たりますが、こちらは不変です。

### balanced の重み

「クラスの重みを balanced にする」とは、件数の少ないクラスの 1 件を重く数えることです。scikit-learn と同じ式（件数 ÷（クラスの数 × そのクラスの件数））にします。

```scala
test("少ないクラスほど 1 件の重みを大きくし、クラスごとの重みの合計をそろえる") {
  val weights = WeightedTrees.balancedWeights(Vector(0, 0, 0, 1))

  assert(weights === Vector(4.0 / 6, 4.0 / 6, 4.0 / 6, 2.0))
  assert(weights.take(3).sum === weights(3) +- 1e-12)
}
```

2 つ目の表明が、この式の意味です。3 件のクラスの重みの合計（2.0）と、1 件のクラスの重み（2.0）がそろいます。

```scala
def balancedWeights(t: Vector[Int]): Vector[Double] =
  val counts = t.groupMapReduce(identity)(_ => 1)(_ + _)
  t.map(label => t.size.toDouble / (counts.size * counts(label)))
```

重みの付け方は `enum` で表します。Scala 3 の `enum` はパラメータを持てるので、表示用の名前を case ごとに持たせられます。

```scala
enum ClassWeight(val label: String):
  case Unweighted extends ClassWeight("none")
  case Balanced extends ClassWeight("balanced")
```

Java 版は列挙定数を `NONE`・`BALANCED` とし、表示もその名前のままにしました。Scala 版は `None` が `Option` の `None` と紛らわしいので `Unweighted` にし、表示だけ Python 版・Java 版と同じ `none`・`balanced` にそろえています。

重みの求め方も `match` 1 つです。`enum` なので、case を足したときに網羅していない `match` はコンパイラが教えてくれます。

```scala
def weightsOf(t: Vector[Int], classWeight: ClassWeight): Vector[Double] =
  classWeight match
    case ClassWeight.Unweighted => t.map(_ => 1.0)
    case ClassWeight.Balanced   => balancedWeights(t)
```

### 重み付けなしなら第 3 章の決定木と同じ

木の構築は、第 3 章の `build` に重みの `Vector` を足した形です。分割の探索では、左右の不純度を **重みの合計** で平均します。

```scala
private[chapter08] def bestSplit(
    x: Vector[Features],
    t: Vector[Int],
    w: Vector[Double]
): Option[Split] =
  if weightedGini(t, w) == 0.0 then None
  else
    val candidates =
      for
        feature <- x.head.columns
        sorted = x.map(_.value(feature)).lazyZip(t).lazyZip(w).toVector.sortBy(_._1)
        i <- 1 until sorted.size
        if sorted(i)._1 != sorted(i - 1)._1
        (leftLabels, rightLabels) = sorted.map(_._2).splitAt(i)
        (leftWeights, rightWeights) = sorted.map(_._3).splitAt(i)
        impurity = (leftWeights.sum * weightedGini(leftLabels, leftWeights)
          + rightWeights.sum * weightedGini(rightLabels, rightWeights)) / w.sum
      yield Split(feature, (sorted(i - 1)._1 + sorted(i)._1) / 2, impurity)
    candidates.reduceOption((best, next) => if next.impurity < best.impurity then next else best)
```

`lazyZip` は 3 つのコレクションを一時的なタプルの列を作らずにまとめる書き方です。第 3 章は値とラベルの 2 つだったので `zip` で足りましたが、重みが増えたのでここで使います。Java 版は「並べ替えた添字の列」を作り、そこから 3 つのリストを引き直していました。

`for ... yield` で候補をすべて作ってから最小値を選ぶ形も第 3 章と同じです。`reduceOption` は「最初に見つけた最小値」を残すので、不純度が同じなら列の順で前の分割が選ばれます。この決め方が Tribuo と違う結果を生むことがあるのは、第 3 章で確かめたとおりです。

重み付けなしなら、第 3 章の決定木と同じ予測になるはずです。深さを変えて確かめます。

```scala
test("重み付けなしなら第 3 章の決定木と同じ予測をする") {
  val x = fareAndAge((8, 30), (9, 22), (13, 18), (20, 45), (60, 25), (80, 33))
  val t = Vector(0, 0, 1, 0, 1, 1)

  Vector(Some(1), Some(2), None).foreach { maxDepth =>
    val chapter03 = maxDepth.fold(DecisionTree.unlimited())(DecisionTree.withMaxDepth)
    val expected = chapter03.fit(x, t.map(_.toString)).predict(x)

    val predictions =
      DecisionTreeClassifier(maxDepth, ClassWeight.Unweighted).fit(x, t).predict(x)

    assert(predictions.map(_.toString) === expected, s"深さ $maxDepth")
  }
}
```

深さの上限は `Option[Int]` で表します。Java 版は「-1 なら制限なし」という約束の定数（`UNLIMITED`）を置きましたが、Scala では `None` がそのまま「上限が無い」ことを表します。`maxDepth.contains(0)` で「上限 0 に達した」を、`maxDepth.map(_ - 1)` で「子の上限」を書けるので、番兵の値を比較する条件分岐が消えます。

### balanced で予測が変わる

重みを付けると何が変わるのかを、最小の例で示します。運賃 2 の乗客 3 人のうち 1 人だけが生存したデータです。

```scala
test("balanced にすると、少ないクラスが混ざった葉でも少ないクラスを予測する") {
  val fare = column("Fare", 1, 1, 1, 1, 2, 2, 2)
  val survived = Vector(0, 0, 0, 0, 0, 0, 1)
  val newX = column("Fare", 1, 2)

  val unweighted = DecisionTreeClassifier(Some(1), ClassWeight.Unweighted).fit(fare, survived)
  val balanced = DecisionTreeClassifier(Some(1), ClassWeight.Balanced).fit(fare, survived)

  assert(unweighted.predict(newX) === Vector(0, 0))
  assert(balanced.predict(newX) === Vector(0, 1))
}
```

重み付けなしでは、運賃 2 の葉は 2 対 1 で死亡が多数派です。`balanced` にすると、生存 1 件の重みは 7 ÷ (2 × 1) = 3.5、死亡 1 件の重みは 7 ÷ (2 × 6) ≒ 0.58 なので、重みの合計は生存 3.5・死亡 1.17 となり、生存を予測します。

## 8.9 前処理とモデルをパイプラインにつなぐ

### 前処理の順番と fit の順番

パイプラインは「前処理の並び」と「モデル」を持ちます。学習では、前の前処理で変換した表で次の前処理を `fit` します。ダミー変数化は、補完が済んでいない表で `fit` すると、欠損値を 1 つのカテゴリとして数えてしまうからです。

```scala
case class Pipeline(transformers: Vector[Transformer], model: DecisionTreeClassifier):
  def fit(x: Table, t: Vector[Int]): FittedPipeline =
    val (fitted, prepared) =
      transformers.foldLeft((Vector.empty[FittedTransformer], x)) {
        case ((fitted, prepared), transformer) =>
          val fittedTransformer = transformer.fit(prepared)
          (fitted :+ fittedTransformer, fittedTransformer.transform(prepared))
      }
    FittedPipeline(fitted, model.fit(FittedPipeline.toFeatures(prepared), t))
```

「学習済みの前処理を集めながら、表を変換していく」という 2 つのことを同時に行うので、`foldLeft` の状態をタプルにします。Java 版は `ArrayList` に追加しながらローカル変数を上書きしました。どちらが読みやすいかは好みですが、Scala 版は途中の値を書き換えないので、「この行の `prepared` はどの段階の表か」を読み違えることがありません。

この章の並びは `Pipeline.build` にまとめます。

```scala
object Pipeline:
  def build(maxDepth: Option[Int], classWeight: ClassWeight): Pipeline =
    Pipeline(
      Vector(
        GroupMedianImputer("Age", Vector("Pclass", "Sex")),
        MostFrequentImputer("Embarked"),
        DummyEncoder(Vector("Sex", "Embarked"))
      ),
      DecisionTreeClassifier(maxDepth, classWeight)
    )
```

### 予測では関数として合成する

予測のときは `fit` は要りません。学習済みの前処理を順につなぐだけです。ここで、ADT にした `FittedTransformer` を **関数として取り出して合成** します。

```scala
case class FittedPipeline(transformers: Vector[FittedTransformer], model: FittedDecisionTree):
  def transform(x: Table): Table =
    transformers.map(_.transform).foldLeft(identity[Table])(_ andThen _)(x)

  def features(x: Table): Vector[Features] = FittedPipeline.toFeatures(transform(x))

  def predict(x: Table): Vector[Int] = model.predict(features(x))
```

`_.transform` はメソッドを `Table => Table` の関数の値にするイータ展開です。`identity[Table]` を初期値に `andThen` で畳み込めば、前処理の並びが 1 つの関数になります。Java 版は `FittedTransformer` を関数型インターフェースにして `default` メソッドの `andThen` を定義しましたが、Scala の `Function1` には最初から `andThen` があるので、合成のためのコードを書く必要はありません。

「データは ADT、つなぎ方は関数」という分け方が、この 3 行に表れています。テストでも、合成した関数とパイプラインの `transform` が同じ表を返すことを確かめます。

```scala
test("前処理を合成した変換は、パイプラインの transform と同じ表を返す") {
  val fitted = Pipeline.build(Some(3), ClassWeight.Unweighted).fit(trainX, trainT)

  val composed = fitted.transformers.map(_.transform).reduce(_ andThen _)

  assert(composed(newPassengers) === fitted.transform(newPassengers))
}
```

表どうしを `===` で比べられるのも、`Table`・`Row` が case class で、中身が不変コレクションだからです。Java 版・C# 版では、配列を持つ型の等価判定を自分で書く必要がありました。

### 前処理が済んだ表を特徴量にする

モデルに渡すのは、第 2 章の `Features`（欠損値を持てない特徴量）です。前処理で埋め残した欠損値があれば、ここで失敗させます。

```scala
private[chapter08] def toFeatures(x: Table): Vector[Features] =
  x.rows.map { row =>
    Features(
      x.columns,
      x.columns.map(column =>
        row.number(column).getOrElse(throw IllegalArgumentException(s"欠損値が残っています: $column"))
      )
    )
  }
```

学習も予測も、この関数を通ってからモデルに届きます。「補完し忘れたデータで学習してしまう」ことは起こりません。

### 欠損値を含むデータで学習して予測する

架空の 8 人（年齢や港が欠けている）で学習し、年齢が欠けた 2 人を予測するテストで、ここまでをつなぎます。

```scala
test("欠損値を含むデータで学習して予測できる") {
  val pipeline = Pipeline.build(Some(3), ClassWeight.Unweighted)

  val fitted = pipeline.fit(trainX, trainT)

  assert(fitted.predict(newPassengers) === Vector(1, 0))
}
```

訓練データは「女性は生存、男性は死亡」という単純な規則にしてあるので、女性を 1、男性を 0 と予測できれば、補完・ダミー変数化・学習・予測のすべてが順につながったことになります。

## 8.10 モデルを保存して読み込む

### Java のシリアライズを試す

Java 版・Kotlin 版は、学習済みのパイプラインを Java のシリアライズで保存し、読み込むクラスを `ObjectInputFilter` の許可リストで絞りました。Scala の case class は既定で `Serializable` なので、同じ形をそのまま書けます。まずはそうしました。

```scala
private val ModelClasses: ObjectInputFilter =
  ObjectInputFilter.Config.createFilter("machinelearning.**;scala.**;java.lang.*;java.util.*;!*")
```

保存はできます。しかし読み込むと、テストが次の例外で落ちました。

```text
java.lang.ClassCastException: cannot assign instance of scala.collection.generic.DefaultSerializationProxy
  to field machinelearning.chapter08.FittedPipeline.transformers of type scala.collection.immutable.Vector
  in instance of machinelearning.chapter08.FittedPipeline
  at java.base/java.io.ObjectStreamClass$FieldReflector.setObjFieldValues(ObjectStreamClass.java:2096)
  at java.base/java.io.ObjectStreamClass$FieldReflector.checkObjectFieldValueTypes(ObjectStreamClass.java:2060)
  at java.base/java.io.ObjectStreamClass.checkObjFieldValueTypes(ObjectStreamClass.java:1349)
  at java.base/java.io.ObjectInputStream$FieldValues.defaultCheckFieldValues(ObjectInputStream.java:2697)
```

Scala の不変コレクションは、自分自身ではなく `DefaultSerializationProxy`（代理のオブジェクト）として書き出され、読み込みの最後に本物へ差し替わります。ところが **`ObjectInputFilter` を設定すると** JDK はフィールドの型検査（`defaultCheckFieldValues`）を行い、差し替わる前の代理を `Vector` 型のフィールドに入れようとして失敗します。フィルタを外せば読み込めますが、それでは許可リストによる安全策を捨てることになります。

### 関数の値は保存できるが、当てにできない

「学習済みの前処理を関数にしていたらどうだったか」も確かめました。Scala 3 の関数の値は `Serializable` なので、書き出して読み戻すこと自体はできます。

```text
書き出せた: Fn$$$Lambda/0x0000000131001000
読み込めた: ok!
```

しかしクラス名は `Fn$$$Lambda/0x...` のような合成された名前で、コンパイルのたびに変わります。許可リストに書くこともできませんし、別のビルドで保存したファイルを読み込める保証もありません。関数ではなく ADT で持つ、という 8.5 節の判断がここで効いてきます。

### ADT をテキストに書き出す

Scala で素直なのは、**ADT を自分でテキストにする** ことでした。前処理も木も case class と `enum` なので、`match` で書き出し、`match` で読み戻せます。決め手は次の 3 つです。

- **依存を増やさない** — JSON のライブラリ（circe など）を足さずに標準ライブラリだけで書ける
- **中身を目で確かめられる** — 保存したファイルをそのまま読めば、どの中央値・どの木が保存されたか分かる
- **任意のクラスを復元しない** — 読み込むのは決まった形のテキストだけなので、許可リストの設計そのものが要らない

形式は「1 行 1 要素のタブ区切り」にしました。実際に保存したファイルは次のようになります（木の行は長いので途中まで）。

```text
format	1
group-median	Age	Pclass,Sex	28.0	1,female=35.0	1,male=45.0	2,female=28.0	2,male=30.0	3,female=22.0	3,male=25.0
most-frequent	Embarked	S
dummy	Sex=male	Embarked=Q,S
tree	N	Sex_male	0.5	0.3562173900302753	N	Pclass	2.5	0.22595740623997618	...
```

書き出しは `match` の 3 つの case です。`sealed trait` にしてあるので、前処理を足したときに書き忘れるとコンパイラが警告します（このプロジェクトでは警告はエラーです）。

```scala
private def render(transformer: FittedTransformer): String =
  val fields = transformer match
    case FittedGroupMedianImputer(column, by, medians, overallMedian) =>
      Vector("group-median", column, by.mkString(","), overallMedian.toString)
        ++ medians.toVector
          .sortBy((group, _) => group.mkString(","))
          .map((group, median) => s"${group.mkString(",")}=$median")
    case FittedMostFrequentImputer(column, mostFrequent) =>
      Vector("most-frequent", column, mostFrequent)
    case FittedDummyEncoder(dummies) =>
      "dummy" +: dummies.map((column, categories) => s"$column=${categories.mkString(",")}")
  fields.mkString("\t")
```

木は行きがけ順（節・左・右）のトークンにします。読むときは「トークンを 1 つ分読んで、残りを返す」関数を再帰で呼び合わせます。パターンマッチの `+:` で「先頭のいくつかと残り」を一度に取り出せるので、添字を進める変数は要りません。

```scala
private def readTree(tokens: Vector[String]): (Tree, Vector[String]) =
  tokens match
    case "L" +: label +: rest => (Leaf(label.toInt), rest)
    case "N" +: feature +: threshold +: impurity +: rest =>
      val (left, afterLeft) = readTree(rest)
      val (right, afterRight) = readTree(afterLeft)
      (Node(Split(feature, threshold.toDouble, impurity.toDouble), left, right), afterRight)
    case _ => throw IllegalArgumentException(s"読み取れない決定木です: ${tokens.mkString("\t")}")
```

`Double` は `toString` で書き、`toDouble` で読みます。この往復は値を変えないので、読み込んだパイプラインは保存したものと等しくなります。テストでもそれを確かめます。

```scala
test("保存したパイプラインを読み込むと同じ値になる") {
  val pipeline = fitted()
  val modelFile = directory.resolve("survived.model")

  ModelFiles.save(pipeline, modelFile)

  assert(ModelFiles.load(modelFile) === pipeline)
}
```

case class の等価判定が中身の比較なので、「同じ予測をする」より強い「同じ値である」をそのまま書けます。Java 版は予測の一致で確かめました。

壊れたファイルや知らない形式も試します。

```scala
test("対応していない形式のファイルは読み込まない") {
  val modelFile = directory.resolve("unknown.model")
  val _ = Files.writeString(modelFile, "format\t99\n", StandardCharsets.UTF_8)

  assertThrows[IllegalArgumentException](ModelFiles.load(modelFile))
}
```

| | Java 版・Kotlin 版 | Scala 版 |
|---|---|---|
| 形式 | Java のシリアライズ（バイナリ） | タブ区切りのテキスト |
| 安全策 | `ObjectInputFilter` の許可リスト | 形式の版と `match` で、決まった形だけを受け付ける |
| 版の互換 | `serialVersionUID`（record は自動） | 先頭行の `format 1` |
| 中身の確認 | 専用のツールが要る | そのまま読める |
| 書く量 | 少ない（20 行ほど） | 多い（100 行ほど） |

書く量は増えますが、増えた分はすべてテストで固定できる純粋な変換です。

## 8.11 評価する

正解率だけでは、クラスの重みの効果が見えません。「テストデータの生存者のうち、何人を生存と予測できたか」も数えます。

```scala
case class Evaluation(
    trainAccuracy: Double,
    testAccuracy: Double,
    foundSurvivors: Int,
    survivors: Int
)
```

```scala
test("正解率と、見つけた生存者の数を求める") {
  val split = TrainTestSplit(trainX.rows, newPassengers.rows, trainT, Vector(1, 1))
  val pipeline = Pipeline.build(Some(3), ClassWeight.Unweighted).fit(trainX, trainT)

  val evaluation = Evaluation.evaluate(pipeline, split)

  assert(evaluation === Evaluation(1.0, 0.5, 1, 2))
}
```

評価の値をまとめて 1 つの case class で比べられるので、表明は 1 行です。Java 版は record の `equals` で同じことを書きました。

```scala
def evaluate(pipeline: FittedPipeline, split: TrainTestSplit[Row, Int]): Evaluation =
  val predictions = pipeline.predict(SurvivedData.features(split.xTest))
  val labels = split.tTest
  Evaluation(
    accuracy(pipeline.predict(SurvivedData.features(split.xTrain)), split.tTrain),
    accuracy(predictions, labels),
    predictions
      .zip(labels)
      .count((prediction, label) => prediction == Survived && label == Survived),
    labels.count(_ == Survived)
  )
```

`zip` してから `count` に 2 引数の関数を渡せるのは、Scala 3 の「タプルの引数をほどく」機能です。Java 版は `IntStream.range` で添字を回しました。

## 8.12 実データでクラスの重みの効果を確かめる

### 実行する

`Main` は、データの件数・分割の件数・重みごとの評価・保存と読み込みを表示します。

```scala
def run(print: String => Unit, modelFile: Path): Unit =
  val rows = Table.load(Paths.get(DataDir.current(), "Survived.csv")).rows
  val t = SurvivedData.target(rows)
  val split = Preprocessing.splitTrainTest(rows, t, TestSize, Seed)
  ...
```

```console
$ sbt "run chapter08"
データ件数: 891（生存 342, 死亡 549）
訓練データ: 712 件, テストデータ: 179 件
classWeight=none: 訓練 0.854, テスト 0.799, 生存者 79 人中 59 人を発見
classWeight=balanced: 訓練 0.848, テスト 0.804, 生存者 79 人中 65 人を発見
保存したモデル: survived.model
架空の乗客の予測: Vector(1, 0)
```

深さ 5 の木で、`balanced` にすると正解率はわずかに上がり（0.799 → 0.804）、見つけた生存者は 59 人から 65 人に増えました。年齢の分からない架空の乗客 2 人（1 等客室の女性と 3 等客室の男性）は、生存・死亡と予測されました。

### Java 版と数値が一致するか

分割は第 2 章で `java.util.Random` と Fisher-Yates を使う形にそろえてあるので、Java 版とまったく同じ行が訓練データとテストデータに入ります。実測した結果は次のとおりで、**Java 版の第 8 章の記事に載っている数値とすべて一致しました**。

| 指標 | Scala 版 | Java 版 |
|------|---------|--------|
| データ件数（生存・死亡） | 891（342・549） | 891（342・549） |
| 訓練・テストの件数 | 712・179 | 712・179 |
| 深さ 5・重み付けなしの正解率（訓練・テスト） | 0.854・0.799 | 0.854・0.799 |
| 深さ 5・balanced の正解率（訓練・テスト） | 0.848・0.804 | 0.848・0.804 |
| 深さ 5 で見つけた生存者（79 人中） | 59 人 → 65 人 | 59 人 → 65 人 |
| 深さ 2 で見つけた生存者（79 人中） | 41 人 → 68 人 | 41 人 → 68 人 |
| Tribuo の CART と予測が違う件数（深さ 1〜10） | 0,0,0,0,2,0,0,0,1,0 | 0,0,0,0,2,0,0,0,1,0 |

Kotlin 版は `kotlin.random.Random` を使うので分割が違い、深さ 5 では `balanced` が見落としを減らしませんでした。同じアルゴリズムでも、1 回の分割の結果から「この設定のほうが良い」と一般化してはいけない、ということです。

### 効果は深さによって変わる

深さ 2 の木では、効果がはっきり出ます。

```scala
test("深さ 2 では balanced にすると見つけられる生存者が 41 人から 68 人に増える") {
  val split = survivedSplit()

  assert(evaluate(split, 2, ClassWeight.Unweighted).foundSurvivors === 41)
  assert(evaluate(split, 2, ClassWeight.Balanced).foundSurvivors === 68)
}
```

浅い木は葉が大きく、多数派に引きずられやすいので、重みを変えた効果が出やすくなります。深い木では葉が小さくなり、重みを付けなくても少数のクラスだけの葉ができるので、差は小さくなります。

### 実データのテスト

実データを使うテストは、データが無ければ `assume` でスキップします。

```scala
private def requireData(): org.scalatest.Assertion =
  assume(Files.exists(csvFile), "学習データ Survived.csv が配置されていない（gulp data:setup）")
```

`assume` は `Assertion` を返すので、`-Wvalue-discard` のもとでは `requireData(): Unit` と書いて「戻り値を捨てる」ことを明示します。第 2 章・第 3 章と同じ書き方です。

前処理の結果も、第 3 章の決定木・Tribuo の CART と突き合わせます。

```scala
test("重み付けなしなら、前処理後のテストデータで第 3 章の決定木と予測が一致する") {
  val split = survivedSplit()

  (1 to 10).foreach { maxDepth =>
    val pipeline = fit(split, maxDepth, ClassWeight.Unweighted)
    val xTrain: Vector[Features] = pipeline.features(SurvivedData.features(split.xTrain))
    val xTest: Vector[Features] = pipeline.features(SurvivedData.features(split.xTest))
    val chapter03 =
      DecisionTree.withMaxDepth(maxDepth).fit(xTrain, split.tTrain.map(_.toString)).predict(xTest)

    assert(pipeline.model.predict(xTest).map(_.toString) === chapter03, s"深さ $maxDepth")
  }
}
```

Tribuo との違いは深さ 5 で 2 件、深さ 9 で 1 件だけでした。第 3 章で確かめたとおり、不純度が同じ分割候補の選び方の違いによるもので、実装の誤りではありません（[ADR 007](../../../adr/007-scala-ml-libraries.md)）。前処理が入ってもこの性質が変わらないことを、深さ 1〜10 で固定しておきます。

## 8.13 探索と可視化

クラス分布、性別・客室クラス別の生存率、木の深さとクラスの重み、混同行列、分割に使われた特徴量の探索と可視化は、[Python 版の 8.13 節](../python/08-classification-and-preprocessing-pipeline.md) と [Kotlin 版の 8.13 節](../kotlin/08-classification-and-preprocessing-pipeline.md) を参照してください。Scala 版では、深さごとの結果を 8.12 節の表とテストにまとめました。

## 8.14 まとめ

この章では、欠損値・カテゴリ値・クラスの偏りを含む現実的なデータで、前処理からモデルの保存までを TDD で実装しました。

1. **データは ADT、つなぎ方は関数** — 学習済みの前処理を `sealed trait` と case class で表し、予測では `transform` を関数の値として取り出して `andThen` で合成した。合成のためのコードを書かずに済み、学習した値は中身として残る
2. **不変コレクションが設計を軽くする** — グループのキーを `Vector` のまま `Map` のキーにでき、`Rows.updated` は防御的コピーなしで「元を変更しない」を守れる。表どうしを `===` で比べられるので、テストの表明も短い
3. **番兵の値を `Option` に置き換える** — 深さの上限は `Option[Int]`。Java 版の `UNLIMITED = -1` のような約束が要らず、`contains(0)`・`map(_ - 1)` で分岐が消えた
4. **保存の方法は言語の事情で変わる** — Java 版・Kotlin 版の Java シリアライズ＋`ObjectInputFilter` は、Scala の不変コレクションの serialization proxy と組み合わさると `ClassCastException` になった。ADT をタブ区切りのテキストにする形に変え、依存を増やさず、中身を読める形にした
5. **1 回の分割の結果を一般化しない** — 分割が同じ Java 版とは数値がすべて一致したが、乱数の違う Kotlin 版では深さ 5 の `balanced` の効果が出なかった。深さによっても効果は変わる

第 15 章の API は、この章の `Pipeline.build` で学習して `ModelFiles.save` で保存したパイプラインを `ModelFiles.load` で読み込み、`SurvivedData.features` で作った表を `FittedPipeline.predict` に渡して予測します。公開している入口は次の 5 つです。

| API | 役割 |
|-----|------|
| `SurvivedData.FeatureColumns` / `SurvivedData.features(rows)` / `SurvivedData.target(rows)` | 特徴量の列の表と正解ラベルを作る |
| `Pipeline.build(maxDepth: Option[Int], classWeight: ClassWeight)` | この章の前処理とモデルの並びを作る |
| `Pipeline.fit(x: Table, t: Vector[Int]): FittedPipeline` | 訓練データで学習する |
| `ModelFiles.save(pipeline, modelFile)` / `ModelFiles.load(modelFile): FittedPipeline` | 学習済みのパイプラインを保存・読み込みする |
| `FittedPipeline.predict(x: Table): Vector[Int]` / `FittedPipeline.features(x: Table)` | 前処理をしてから予測する／前処理だけを行う |

次の章では、住宅価格のデータを使い、標準化や多項式特徴量など、特徴量そのものを作り変える方法を学びます。

<details>
<summary>完成コード: 前処理（Transformer・FittedTransformer・GroupMedianImputer・MostFrequentImputer・DummyEncoder）</summary>

```scala
// src/main/scala/machinelearning/chapter08/Transformers.scala
package machinelearning.chapter08

import machinelearning.chapter02.{Row, Table}

/** 訓練データから変換に必要な値を求める前処理。 */
trait Transformer:
  def fit(x: Table): FittedTransformer

/** fit で求めた値を使ってデータを変換する前処理。
  *
  * 関数の値（Table => Table）ではなく sealed trait と case class で表す。そうするとどんな前処理があるかを
  * コンパイラが数え上げられ、学習済みの値をそのままファイルに書き出せる。関数として合成したいときは transform を関数の値として取り出す。
  */
sealed trait FittedTransformer:
  def transform(x: Table): Table

/** 行を書き換えた新しい行を作る。 */
private[chapter08] object Rows:
  /** 列の値を置き換えた（列が無ければ足した）新しい行を返す。元の行は変更しない。 */
  def updated(row: Row, column: String, value: String): Row =
    Row(row.cells.updated(column, value))

/** 数値の列の欠損値を、同じグループ（by の列の値の組）の中央値で補完する前処理。 */
case class GroupMedianImputer(column: String, by: Vector[String]) extends Transformer:
  override def fit(x: Table): FittedTransformer =
    val known = x.rows.filterNot(_.isMissing(column))
    val groups = known.groupMap(GroupMedianImputer.groupOf(_, by))(_.number(column).get)
    FittedGroupMedianImputer(
      column,
      by,
      groups.view.mapValues(GroupMedianImputer.median).toMap,
      GroupMedianImputer.median(known.map(_.number(column).get))
    )

object GroupMedianImputer:
  /** 行のグループ。by の列の値を並べた Vector で、Map のキーに使う。 */
  def groupOf(row: Row, by: Vector[String]): Vector[String] = by.map(row.text)

  /** 中央値。件数が偶数なら中央の 2 つの平均。 */
  def median(values: Vector[Double]): Double =
    val sorted = values.sorted
    val middle = sorted.size / 2
    if sorted.size % 2 == 1 then sorted(middle) else (sorted(middle - 1) + sorted(middle)) / 2

/** fit で求めたグループごとの中央値と全体の中央値を持ち、欠損値を補完する。 */
case class FittedGroupMedianImputer(
    column: String,
    by: Vector[String],
    medians: Map[Vector[String], Double],
    overallMedian: Double
) extends FittedTransformer:
  override def transform(x: Table): Table = Table(x.columns, x.rows.map(fill))

  private def fill(row: Row): Row =
    if !row.isMissing(column) then row
    else
      val median = medians.getOrElse(GroupMedianImputer.groupOf(row, by), overallMedian)
      Rows.updated(row, column, median.toString)

/** 文字列の列の欠損値を、訓練データで最も多い値（最頻値）で補完する前処理。 */
case class MostFrequentImputer(column: String) extends Transformer:
  override def fit(x: Table): FittedTransformer =
    val counts = x.rows.filterNot(_.isMissing(column)).groupMapReduce(_.text(column))(_ => 1)(_ + _)
    // maxBy は最初の最大値を返すので、同数なら値の順で前のものを選ぶ
    FittedMostFrequentImputer(column, counts.toVector.sortBy(_._1).maxBy(_._2)._1)

/** fit で求めた最頻値を持ち、欠損値を補完する。 */
case class FittedMostFrequentImputer(column: String, mostFrequent: String)
    extends FittedTransformer:
  override def transform(x: Table): Table =
    Table(
      x.columns,
      x.rows.map(row =>
        if row.isMissing(column) then Rows.updated(row, column, mostFrequent) else row
      )
    )

/** カテゴリ値の列を、最初のカテゴリを除いたカテゴリごとの 0 と 1 の列（ダミー変数）にする前処理。 */
case class DummyEncoder(columns: Vector[String]) extends Transformer:
  override def fit(x: Table): FittedTransformer =
    FittedDummyEncoder(columns.map(column => column -> DummyEncoder.categoriesOf(x, column).tail))

object DummyEncoder:
  /** 列の値を重複なく並べ替える。欠損値は除く。 */
  private def categoriesOf(x: Table, column: String): Vector[String] =
    x.rows.filterNot(_.isMissing(column)).map(_.text(column)).distinct.sorted

/** fit で求めたカテゴリを持ち、どのデータにも同じダミー変数の列を作る。
  *
  * Map は順序を保たないので、列の順を保つために (列名, カテゴリ) の組の Vector で持つ。
  */
case class FittedDummyEncoder(dummies: Vector[(String, Vector[String])]) extends FittedTransformer:
  override def transform(x: Table): Table =
    val columns = dummies.foldLeft(x.columns) { case (columns, (column, categories)) =>
      columns.filterNot(_ == column) ++ categories.map(category => s"${column}_$category")
    }
    Table(columns, x.rows.map(encode))

  private def encode(row: Row): Row =
    dummies.foldLeft(row) { case (encoded, (column, categories)) =>
      val value = row.text(column)
      categories.foldLeft(encoded) { (encoded, category) =>
        Rows.updated(encoded, s"${column}_$category", if value == category then "1" else "0")
      }
    }
```

</details>

<details>
<summary>完成コード: 重み付きの決定木（ClassWeight・Tree・WeightedTrees・DecisionTreeClassifier）</summary>

```scala
// src/main/scala/machinelearning/chapter08/WeightedTrees.scala
package machinelearning.chapter08

import machinelearning.chapter02.Features
import machinelearning.chapter03.Split
import scala.collection.immutable.ListMap

/** クラスの重みの付け方。label は表示に使う名前。 */
enum ClassWeight(val label: String):
  /** 重みを付けない（すべて 1） */
  case Unweighted extends ClassWeight("none")

  /** クラスの件数に反比例する重みを付ける */
  case Balanced extends ClassWeight("balanced")

/** 重み付きの決定木。葉か節のどちらかで、ほかの実装は許さない（enum で閉じる）。 */
enum Tree:
  case Leaf(label: Int)
  case Node(split: Split, left: Tree, right: Tree)

/** 1 件ごとの重みを使って決定木を作り、予測する関数。 */
object WeightedTrees:
  import Tree.*

  /** 重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。 */
  def weightedGini(labels: Vector[Int], weights: Vector[Double]): Double =
    val total = weights.sum
    1.0 - weightSums(labels, weights).values.map(weight => math.pow(weight / total, 2)).sum

  /** クラスの件数に反比例する重み（件数 / (クラスの数 × そのクラスの件数)）を 1 件ごとに求める。 */
  def balancedWeights(t: Vector[Int]): Vector[Double] =
    val counts = t.groupMapReduce(identity)(_ => 1)(_ + _)
    t.map(label => t.size.toDouble / (counts.size * counts(label)))

  /** クラスの重みの付け方から、1 件ごとの重みを求める。 */
  def weightsOf(t: Vector[Int], classWeight: ClassWeight): Vector[Double] =
    classWeight match
      case ClassWeight.Unweighted => t.map(_ => 1.0)
      case ClassWeight.Balanced   => balancedWeights(t)

  /** 重みの合計が最も大きいラベル。同じなら先に現れたラベルを選ぶ。 */
  private[chapter08] def weightedMajority(labels: Vector[Int], weights: Vector[Double]): Int =
    weightSums(labels, weights).maxBy(_._2)._1

  /** 左右の重み付き不純度の、重みによる平均が最も小さくなる分割。同じ不純度なら先に見つけた分割を選ぶ。 */
  private[chapter08] def bestSplit(
      x: Vector[Features],
      t: Vector[Int],
      w: Vector[Double]
  ): Option[Split] =
    if weightedGini(t, w) == 0.0 then None
    else
      val candidates =
        for
          feature <- x.head.columns
          sorted = x.map(_.value(feature)).lazyZip(t).lazyZip(w).toVector.sortBy(_._1)
          i <- 1 until sorted.size
          if sorted(i)._1 != sorted(i - 1)._1
          (leftLabels, rightLabels) = sorted.map(_._2).splitAt(i)
          (leftWeights, rightWeights) = sorted.map(_._3).splitAt(i)
          impurity = (leftWeights.sum * weightedGini(leftLabels, leftWeights)
            + rightWeights.sum * weightedGini(rightLabels, rightWeights)) / w.sum
        yield Split(feature, (sorted(i - 1)._1 + sorted(i)._1) / 2, impurity)
      candidates.reduceOption((best, next) => if next.impurity < best.impurity then next else best)

  /** 深さの上限まで分割を繰り返して木を作る。maxDepth が None なら上限なし。 */
  private[chapter08] def build(
      x: Vector[Features],
      t: Vector[Int],
      w: Vector[Double],
      maxDepth: Option[Int]
  ): Tree =
    val split = if maxDepth.contains(0) then None else bestSplit(x, t, w)
    split match
      case None => Leaf(weightedMajority(t, w))
      case Some(s) =>
        val (left, right) = x.indices.toVector.partition(i => goesLeft(s, x(i)))
        val childDepth = maxDepth.map(_ - 1)
        Node(
          s,
          build(left.map(x), left.map(t), left.map(w), childDepth),
          build(right.map(x), right.map(t), right.map(w), childDepth)
        )

  /** 1 件の特徴量のラベルを予測する。 */
  def predictOne(tree: Tree, features: Features): Int =
    tree match
      case Leaf(label) => label
      case Node(split, left, right) =>
        predictOne(if goesLeft(split, features) then left else right, features)

  private def goesLeft(split: Split, features: Features): Boolean =
    features.value(split.feature) <= split.threshold

  /** ラベルごとの重みの合計を、ラベルが先に現れた順に並べて返す。 */
  private def weightSums(labels: Vector[Int], weights: Vector[Double]): Map[Int, Double] =
    labels.zip(weights).foldLeft(ListMap.empty[Int, Double]) { case (sums, (label, weight)) =>
      sums.updated(label, sums.getOrElse(label, 0.0) + weight)
    }

/** クラスの重みを付けられる決定木の分類器。fit で学習済みの木を返す。maxDepth が None なら深さの上限なし。 */
case class DecisionTreeClassifier(maxDepth: Option[Int], classWeight: ClassWeight):
  /** 訓練データから木を作る。 */
  def fit(x: Vector[Features], t: Vector[Int]): FittedDecisionTree =
    FittedDecisionTree(WeightedTrees.build(x, t, WeightedTrees.weightsOf(t, classWeight), maxDepth))

/** 学習済みの決定木。 */
case class FittedDecisionTree(tree: Tree):
  /** 特徴量ごとのラベルを予測する。 */
  def predict(x: Vector[Features]): Vector[Int] = x.map(WeightedTrees.predictOne(tree, _))
```

</details>

<details>
<summary>完成コード: パイプラインと評価（SurvivedData・Pipeline・FittedPipeline・Evaluation）</summary>

```scala
// src/main/scala/machinelearning/chapter08/Pipeline.scala
package machinelearning.chapter08

import machinelearning.chapter02.{Features, Row, Table, TrainTestSplit}

/** Survived.csv の特徴量の列と正解ラベルの列。 */
object SurvivedData:
  /** モデルに渡す特徴量の列 */
  val FeatureColumns: Vector[String] =
    Vector("Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked")

  /** 正解ラベルの列（1 が生存、0 が死亡） */
  val Target = "Survived"

  /** 行を、特徴量の列だけを並べた表にする。行がほかの列を持っていても使わない。 */
  def features(rows: Vector[Row]): Table = Table(FeatureColumns, rows)

  /** 行の Survived 列を、整数の正解ラベルにする。 */
  def target(rows: Vector[Row]): Vector[Int] = rows.map(_.text(Target).toInt)

/** 前処理を順に fit・transform してから、モデルを学習する。 */
case class Pipeline(transformers: Vector[Transformer], model: DecisionTreeClassifier):

  /** 訓練データで前処理とモデルを学習する。前処理は、前の前処理で変換したデータで fit する。 */
  def fit(x: Table, t: Vector[Int]): FittedPipeline =
    val (fitted, prepared) =
      transformers.foldLeft((Vector.empty[FittedTransformer], x)) {
        case ((fitted, prepared), transformer) =>
          val fittedTransformer = transformer.fit(prepared)
          (fitted :+ fittedTransformer, fittedTransformer.transform(prepared))
      }
    FittedPipeline(fitted, model.fit(FittedPipeline.toFeatures(prepared), t))

object Pipeline:
  /** Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に、決定木で分類する。 */
  def build(maxDepth: Option[Int], classWeight: ClassWeight): Pipeline =
    Pipeline(
      Vector(
        GroupMedianImputer("Age", Vector("Pclass", "Sex")),
        MostFrequentImputer("Embarked"),
        DummyEncoder(Vector("Sex", "Embarked"))
      ),
      DecisionTreeClassifier(maxDepth, classWeight)
    )

/** 学習済みの前処理とモデル。予測するときは前処理の transform だけを使う。 */
case class FittedPipeline(transformers: Vector[FittedTransformer], model: FittedDecisionTree):

  /** 学習済みの前処理を順に合成して、データを変換する。 */
  def transform(x: Table): Table =
    transformers.map(_.transform).foldLeft(identity[Table])(_ andThen _)(x)

  /** 前処理をして、モデルに渡す特徴量にする。 */
  def features(x: Table): Vector[Features] = FittedPipeline.toFeatures(transform(x))

  /** 前処理をしてから、1 行ごとのラベル（1 が生存、0 が死亡）を予測する。 */
  def predict(x: Table): Vector[Int] = model.predict(features(x))

object FittedPipeline:
  /** 前処理の済んだ表を特徴量にする。欠損値が残っていれば失敗する。 */
  private[chapter08] def toFeatures(x: Table): Vector[Features] =
    x.rows.map { row =>
      Features(
        x.columns,
        x.columns.map(column =>
          row.number(column).getOrElse(throw IllegalArgumentException(s"欠損値が残っています: $column"))
        )
      )
    }

/** 訓練データとテストデータの正解率と、テストデータの生存者のうち生存と予測できた人数。 */
case class Evaluation(
    trainAccuracy: Double,
    testAccuracy: Double,
    foundSurvivors: Int,
    survivors: Int
)

object Evaluation:
  private val Survived = 1

  /** 学習済みのパイプラインを、訓練データとテストデータで評価する。 */
  def evaluate(pipeline: FittedPipeline, split: TrainTestSplit[Row, Int]): Evaluation =
    val predictions = pipeline.predict(SurvivedData.features(split.xTest))
    val labels = split.tTest
    Evaluation(
      accuracy(pipeline.predict(SurvivedData.features(split.xTrain)), split.tTrain),
      accuracy(predictions, labels),
      predictions
        .zip(labels)
        .count((prediction, label) => prediction == Survived && label == Survived),
      labels.count(_ == Survived)
    )

  private def accuracy(predictions: Vector[Int], labels: Vector[Int]): Double =
    predictions.zip(labels).count((prediction, label) => prediction == label).toDouble / labels.size
```

</details>

<details>
<summary>完成コード: モデルの保存と読み込み（ModelFiles）</summary>

```scala
// src/main/scala/machinelearning/chapter08/ModelFiles.scala
package machinelearning.chapter08

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import machinelearning.chapter03.Split
import scala.jdk.CollectionConverters.*

/** 学習済みのパイプラインを、タブ区切りのテキストとして保存し、読み込む。
  *
  * Java のシリアライズは使わない。Scala の不変コレクションは serialization proxy を通して書かれるので、 読み込むクラスを ObjectInputFilter
  * で絞ると proxy の解決前に型検査が走り、ClassCastException になる。 前処理もモデルも ADT
  * なので、自分でテキストに書き出すほうが素直で、ファイルを読んで中身を確かめられる。
  */
object ModelFiles:
  import Tree.*

  /** 形式の版。読み込むときに確かめる */
  private val Version = "format\t1"

  /** パイプライン全体（前処理で求めた値とモデル）をテキストで保存する。 */
  def save(pipeline: FittedPipeline, modelFile: Path): Unit =
    val _ = Files.createDirectories(modelFile.toAbsolutePath.getParent)
    val _ = Files.writeString(modelFile, render(pipeline), StandardCharsets.UTF_8)

  /** 保存したパイプラインを読み込む。形式が違えば失敗する。 */
  def load(modelFile: Path): FittedPipeline =
    parse(Files.readAllLines(modelFile, StandardCharsets.UTF_8).asScala.toVector)

  /** パイプラインを、1 行 1 要素のテキストにする。最後の行が決定木。 */
  private[chapter08] def render(pipeline: FittedPipeline): String =
    (Vector(Version) ++ pipeline.transformers.map(render) :+ render(pipeline.model.tree))
      .mkString("", "\n", "\n")

  private def render(transformer: FittedTransformer): String =
    val fields = transformer match
      case FittedGroupMedianImputer(column, by, medians, overallMedian) =>
        Vector("group-median", column, by.mkString(","), overallMedian.toString)
          ++ medians.toVector
            .sortBy((group, _) => group.mkString(","))
            .map((group, median) => s"${group.mkString(",")}=$median")
      case FittedMostFrequentImputer(column, mostFrequent) =>
        Vector("most-frequent", column, mostFrequent)
      case FittedDummyEncoder(dummies) =>
        "dummy" +: dummies.map((column, categories) => s"$column=${categories.mkString(",")}")
    fields.mkString("\t")

  private def render(tree: Tree): String = ("tree" +: tokensOf(tree)).mkString("\t")

  /** 木を行きがけ順（節・左・右）のトークンにする。 */
  private def tokensOf(tree: Tree): Vector[String] =
    tree match
      case Leaf(label) => Vector("L", label.toString)
      case Node(Split(feature, threshold, impurity), left, right) =>
        Vector("N", feature, threshold.toString, impurity.toString)
          ++ tokensOf(left) ++ tokensOf(right)

  private[chapter08] def parse(lines: Vector[String]): FittedPipeline =
    require(lines.headOption.contains(Version), "対応していない形式のモデルです")
    val (treeLines, transformerLines) =
      lines.tail.filter(_.nonEmpty).partition(_.startsWith("tree\t"))
    require(treeLines.size == 1, "決定木の行がありません")
    FittedPipeline(
      transformerLines.map(parseTransformer),
      FittedDecisionTree(parseTree(treeLines.head))
    )

  private def parseTransformer(line: String): FittedTransformer =
    line.split("\t", -1).toVector match
      case "group-median" +: column +: by +: overallMedian +: medians =>
        FittedGroupMedianImputer(
          column,
          by.split(",", -1).toVector,
          medians.map { entry =>
            val (group, median) = splitOnce(entry, '=')
            group.split(",", -1).toVector -> median.toDouble
          }.toMap,
          overallMedian.toDouble
        )
      case Vector("most-frequent", column, mostFrequent) =>
        FittedMostFrequentImputer(column, mostFrequent)
      case "dummy" +: entries =>
        FittedDummyEncoder(entries.map { entry =>
          val (column, categories) = splitOnce(entry, '=')
          column -> (if categories.isEmpty then Vector.empty
                     else categories.split(",", -1).toVector)
        })
      case _ => throw IllegalArgumentException(s"読み取れない前処理です: $line")

  private def parseTree(line: String): Tree =
    val (tree, rest) = readTree(line.split("\t", -1).toVector.tail)
    require(rest.isEmpty, "決定木の後ろに余分なものがあります")
    tree

  /** 行きがけ順のトークンから木を 1 つ読み、残りのトークンと一緒に返す。 */
  private def readTree(tokens: Vector[String]): (Tree, Vector[String]) =
    tokens match
      case "L" +: label +: rest => (Leaf(label.toInt), rest)
      case "N" +: feature +: threshold +: impurity +: rest =>
        val (left, afterLeft) = readTree(rest)
        val (right, afterRight) = readTree(afterLeft)
        (Node(Split(feature, threshold.toDouble, impurity.toDouble), left, right), afterRight)
      case _ => throw IllegalArgumentException(s"読み取れない決定木です: ${tokens.mkString("\t")}")

  /** 最初の区切り文字で 2 つに分ける。区切り文字が無ければ失敗する。 */
  private def splitOnce(text: String, separator: Char): (String, String) =
    val index = text.indexOf(separator.toInt)
    require(index >= 0, s"'$separator' がありません: $text")
    (text.take(index), text.drop(index + 1))
```

</details>

<details>
<summary>完成コード: 実行する入口（Main）</summary>

```scala
// src/main/scala/machinelearning/chapter08/Main.scala
package machinelearning.chapter08

import java.nio.file.{Path, Paths}
import java.util.Locale
import machinelearning.chapter02.{Preprocessing, Row, Table}
import machinelearning.dataset.DataDir

/** クラスの重みごとの評価結果を表示し、学習済みのパイプラインを保存して読み込む。 */
object Main:
  /** 学習済みのパイプラインの保存先（apps/scala/model/ は .gitignore の対象） */
  val ModelFile: Path = Paths.get("model", "survived.model")

  private val TestSize = 0.2
  private val Seed = 0L
  private val MaxDepth = Some(5)

  /** 年齢が分からない架空の乗客 2 人（1 等客室の女性と、3 等客室の男性） */
  private val NewPassengers: Table =
    SurvivedData.features(
      Vector(
        passenger("1", "female", "", "0", "0", "50", "C"),
        passenger("3", "male", "", "0", "0", "8", "S")
      )
    )

  /** 特徴量の列の順に並べた値から、乗客 1 人分の行を作る。空文字列は欠損値。 */
  private def passenger(values: String*): Row =
    Row(SurvivedData.FeatureColumns.zip(values.toVector).toMap)

  def run(print: String => Unit): Unit = run(print, ModelFile)

  /** 保存先を指定して実行する。テストから一時ディレクトリを渡すために使う。 */
  def run(print: String => Unit, modelFile: Path): Unit =
    val rows = Table.load(Paths.get(DataDir.current(), "Survived.csv")).rows
    val t = SurvivedData.target(rows)
    val split = Preprocessing.splitTrainTest(rows, t, TestSize, Seed)
    val survived = t.count(_ == 1)
    print(s"データ件数: ${rows.size}（生存 $survived, 死亡 ${t.size - survived}）")
    print(s"訓練データ: ${split.xTrain.size} 件, テストデータ: ${split.xTest.size} 件")

    val pipelines = ClassWeight.values.toVector.map { classWeight =>
      val pipeline =
        Pipeline.build(MaxDepth, classWeight).fit(SurvivedData.features(split.xTrain), split.tTrain)
      val result = Evaluation.evaluate(pipeline, split)
      print(
        s"classWeight=${classWeight.label}: 訓練 ${format(result.trainAccuracy)}, " +
          s"テスト ${format(result.testAccuracy)}, " +
          s"生存者 ${result.survivors} 人中 ${result.foundSurvivors} 人を発見"
      )
      classWeight -> pipeline
    }.toMap

    ModelFiles.save(pipelines(ClassWeight.Balanced), modelFile)
    val predictions = ModelFiles.load(modelFile).predict(NewPassengers)
    print(s"保存したモデル: ${modelFile.getFileName}")
    print(s"架空の乗客の予測: $predictions")

  private def format(value: Double): String = String.format(Locale.ROOT, "%.3f", value)
```

</details>
