---
type: Article
title: "第 2 章: データの前処理と三角測量"
description: "case class と不変のコレクションで表を自作し、Option で欠損値を表して iris データを前処理し、java.util.Random による訓練・テストデータ分割を Scala の TDD で実装する。"
tags: [article,getting-start-ml,scala]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T03:10:00Z }
---

# 第 2 章: データの前処理と三角測量

## 2.1 はじめに

第 1 章では、欠損のないきれいなデータを読み込み、人間が書いたルールで判定しました。現実のデータには、空欄（欠損値）が混ざっています。

この章では、アヤメ（iris）のデータを題材に、欠損値を補完し、データを **訓練データ** と **テストデータ** に分けるところまでを TDD で実装します。

[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md)・[Java 版の第 2 章](../java/02-data-preprocessing-and-triangulation.md) と同じ TODO リストで進めます。Python 版は pandas、Kotlin 版は Kotlin DataFrame を使いましたが、Scala 版は Java 版・C# 版と同じく **データフレームのライブラリを使わず**、小さな表の型を自分で作ります。Scala では、その型を `case class` と不変のコレクション（`Map`・`Vector`）で書けます。次の 3 点に注目してください。

- **`Option` で欠損値を表す** — F# 版の `option` と同じ形です。Java 版の `OptionalDouble`・C# 版の `double?` との違いを見ます
- **`Vector` なら値で比べられる** — Java 版・C# 版は「`double` の配列を包んで `equals` を自分で書く」工夫が要りましたが、Scala の `case class` と `Vector` の組では既定でそうなります
- **Java 版と同じ乱数で分ける** — 分割に `java.util.Random` と Fisher-Yates を使うので、並べ替えの結果も、そこから求まる平均値も Java 版と一致します

## 2.2 題材とデータ

### iris.csv

`iris.csv` には、アヤメの花 150 件の測定値と品種が記録されています。

| 列 | 意味 | Scala 版での読み方 |
|----|------|------------------|
| がく片長さ | がく片の長さ | `row.number("がく片長さ")` → `Option[Double]` |
| がく片幅 | がく片の幅 | `row.number("がく片幅")` → `Option[Double]` |
| 花弁長さ | 花弁の長さ | `row.number("花弁長さ")` → `Option[Double]` |
| 花弁幅 | 花弁の幅 | `row.number("花弁幅")` → `Option[Double]` |
| 種類 | 品種（3 種類が 50 件ずつ） | `row.text("種類")` → `String` |

特徴量の 4 列には合わせて 7 件の欠損値があります。`Option[Double]` は「値があるかもしれないし、無いかもしれない」を表す型で、`Some(0.1)` か `None` のどちらかです。F# 版の `float option` と同じ形で、Java 版の `OptionalDouble`・C# 版の `double?` に当たります。

| 言語 | 欠損値の表し方 | 値を取り出す |
|------|--------------|------------|
| Scala | `Option[Double]`（`Some`／`None`） | `getOrElse`・`map`・`flatMap`・`match` |
| F# | `float option`（`Some`／`None`） | `Option.defaultValue`・`match` |
| Java | `OptionalDouble`（空か否か） | `orElseGet`・`getAsDouble` |
| C# | `double?` | `??`・`GetValueOrDefault` |
| Kotlin | `Double?` | `?:` |

### 訓練データとテストデータ

モデルの良し悪しは、学習に使っていないデータでどれだけ当たるかで測ります。そこで、150 件を 7 対 3 の 105 件（訓練データ）と 45 件（テストデータ）に分けます。欠損値を補完する平均値は **訓練データだけ** から求め、その値で両方を補完します。データ全体の平均値を使うと、テストデータの情報が学習側に漏れるためです（データリーク）。

### Java 版と数値が一致する理由

分け方の手順（シード付きで並べ替え、テストデータの件数を切り上げる）は、どの言語版も同じです。違うのは乱数生成器で、Python 版は NumPy、Kotlin 版は `kotlin.random.Random`、F# 版は .NET の `Random` を使うので、どの行がテストデータに入るかは一致しません。

Scala 版は、ここであえて **Java 版と同じ `java.util.Random` と同じ手順（Fisher-Yates）** を選びました。`scala.util.Random.shuffle` を使うほうが短く書けますが、Java 版と数値を突き合わせられません。同じ乱数・同じ手順にすれば、「JVM の上で同じことを書いたら本当に同じ結果になるのか」をテストで確かめられます（2.8 節）。結果として、訓練データの平均値も、第 3 章の正解率も、決定木の境界も Java 版と一致します。

## 2.3 開発環境の準備

この章では依存を追加しません。CSV の読み込みも前処理も、第 1 章と同じ標準ライブラリ（`java.nio.file` と Scala の不変なコレクション）で書きます。

Kotlin 版は Kotlin DataFrame を使いましたが、Scala 版ではデータフレームのライブラリを使わないと決めました（[ADR 007](../../../adr/007-scala-ml-libraries.md)）。Scala にも Spark の DataFrame がありますが、この題材には重すぎます。表の操作を自分で書くほうが、欠損値の表し方や補完の前後の区別を型の設計として考えられます。

## 2.4 TODO リストの作成

**TODO リスト**:

- [ ] iris.csv を読み込む
  - [ ] BOM 付き CSV の列名に BOM が残らない
  - [ ] 空欄を欠損値（`None`）として読み込む
  - [ ] 行末の空欄も欠損値として読み込む
  - [ ] 無い列を読み出すと列名を示すエラーになる
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
  - [ ] Java 版と同じ並べ替えになる
- [ ] 訓練データの平均値で両方を補完する
- [ ] 実データで前処理の結果を表示する

Java 版と同じく「行末の空欄も欠損値として読み込む」を入れています。自分で CSV を分割するので、ライブラリが面倒を見てくれていた落とし穴を自分で踏むことになるためです（2.5 節）。最後の「Java 版と同じ並べ替えになる」は Scala 版だけの項目です。

## 2.5 不変の表で読み込む

### データの表し方を決める

データフレームのライブラリを使わない代わりに、次の 3 つの `case class` を作ります。

| 型 | 持つもの | 役割 |
|----|---------|------|
| `Table` | 列名の `Vector` と `Row` の `Vector` | 読み込んだ CSV 全体 |
| `Row` | セルの文字列（列名 → 文字列の `Map`） | 1 行分。`number` と `text` で読み出す |
| `Features` | 列名の `Vector` と値の `Vector[Double]` | 補完が済んだ 1 行分の特徴量。欠損値を持てない |

`Row` はセルを **文字列のまま** 持ち、読み出すときに `number`（数値として読む。空欄なら `None`）と `text`（文字列として読む）を使い分けます。iris 専用の `case class`（`IrisRow(sepalLength: Double, ...)`）を作る案もありましたが、第 7 章以降では列の違う CSV を何種類も読むので、列名で引く形のほうが章をまたいで使い回せます。

`Row` と `Features` を分けたのは、**補完する前と後を型で区別する** ためです。`Row` の数値は欠損しうるので `Option[Double]` で返りますが、`Features` の値は `Vector[Double]` で、欠損値を入れる場所がありません。補完する前の行をうっかりモデルに渡すと、コンパイルエラーになります。F# 版の `Map<string, float option>` と `Map<string, float>` の区別、Java 版の `OptionalDouble` と `double` の区別がこれに当たります。

### テストファースト

読み込みのテストを書きます。第 1 章と同じく、一時ディレクトリに架空の値の CSV を作ります。

```scala
// src/test/scala/machinelearning/chapter02/TableSpec.scala
class TableSpec extends AnyFunSuite:
  private val header = "\uFEFFがく片長さ,がく片幅,花弁長さ,花弁幅,種類\n"

  private def writeCsv(rows: String): Path =
    val file = Files.createTempDirectory("ml-scala-").resolve("iris.csv")
    Files.writeString(file, header + rows)

  test("CSV を読み込むと列名の並びを保ち、BOM は残らない") {
    val table = Table.load(writeCsv("0.1,0.2,0.3,0.4,Iris-setosa\n"))

    assert(table.columns === Vector("がく片長さ", "がく片幅", "花弁長さ", "花弁幅", "種類"))
  }

  test("空欄は欠損値の None として読み込む") {
    val row = Table.load(writeCsv("0.1,,0.3,0.4,Iris-setosa\n")).rows.head

    assert(row.number("がく片幅") === None)
    assert(row.number("がく片長さ") === Some(0.1))
    assert(row.text("種類") === "Iris-setosa")
  }
```

`Option` はふつうの値なので、`=== None` や `=== Some(0.1)` とそのまま比べられます。Java 版が AssertJ の `isEmpty()`・`hasValue(0.1)` という専用の検査を使ったところが、Scala では等値比較で済みます。

`Table` と `Row` がまだ無いので、テストの実行より前にコンパイルが止まります（Red）。

```text
[error] -- [E006] Not Found Error: .../chapter02/TableSpec.scala:14:16
[error] 14 |    val table = Table.load(writeCsv("0.1,0.2,0.3,0.4,Iris-setosa\n"))
[error]    |                ^^^^^
[error]    |                Not found: Table - did you mean table?
[error]    |
[error]    | longer explanation available when compiling with `-explain`
[error] -- [E006] Not Found Error: .../chapter02/TableSpec.scala:20:14
[error] 20 |    val row = Table.load(writeCsv("0.1,,0.3,0.4,Iris-setosa\n")).rows.head
[error]    |              ^^^^^
[error]    |              Not found: Table - did you mean Tuple?
[error]    |
[error]    | longer explanation available when compiling with `-explain`
[error] 5 errors found
[error] (Test / compileIncremental) Compilation failed
```

E006 は Scala 3 の「名前が見つからない」エラーの番号です。`Not found: Table - did you mean table?` のように、似た名前を提案してくれます（ここでは同じテストの中の変数 `table` を指しています）。Java 版・Kotlin 版と同じく、テストを実行する前にコンパイラが止めてくれるのが JVM の言語の Red です。

### Green: 明白な実装

第 1 章の `loadPeople` と同じ手順（BOM を取り除き、ヘッダー行で列名を決め、各行を分割する）なので、明白な実装で書きます。

```scala
// src/main/scala/machinelearning/chapter02/Table.scala
/** CSV の 1 行。セルの文字列を列名で引けるように持つ。空欄は欠損値として扱う。 */
case class Row(cells: Map[String, String]):

  /** 数値の列を読む。空欄なら None を返す。 */
  def number(column: String): Option[Double] =
    val cell = text(column)
    if cell.trim.isEmpty then None else Some(cell.toDouble)

  /** 文字列の列を読む。 */
  def text(column: String): String =
    cells.getOrElse(column, throw IllegalArgumentException(s"列がありません: $column"))

  /** セルが空欄かどうか。 */
  def isMissing(column: String): Boolean = text(column).trim.isEmpty
```

- `case class Row(cells: Map[String, String])` の `Map` は Scala の **不変な** `Map` です。作ったら変更できないので、Java 版が `Map.copyOf` で写しを取り、C# 版が `ImmutableDictionary` を使ったところに当たる手当てが要りません。呼び出し側が後から中身を書き換えることもできません
- `Option[Double]` は `Some(値)` か `None` のどちらかです。`null` ではなく値として欠損を表すので、読む側は取り出す前に有無を考えざるを得ません
- `cells.getOrElse(column, throw ...)` は、キーがあればその値を、無ければ第 2 引数を評価します。`getOrElse` の第 2 引数は **名前渡し**（使うときに初めて評価される）なので、そこに `throw` を書けます。無い列を読むと、列名を含むメッセージの例外になります

```scala
/** 列名の並びと行のリスト。データフレームのライブラリの代わりに使う、変更できない表。 */
case class Table(columns: Vector[String], rows: Vector[Row]):

  /** 列ごとの欠損値の数を、列の順に並べて返す。 */
  def countMissing: Vector[(String, Int)] =
    columns.map(column => column -> rows.count(_.isMissing(column)))

object Table:
  private val Bom = "\uFEFF"

  /** BOM 付きの UTF-8 の CSV を読み込む。 */
  def load(csvFile: Path): Table =
    val lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8).asScala.toVector
    val columns = lines.head.stripPrefix(Bom).split(",", -1).toVector
    val rows = lines.tail.filter(_.trim.nonEmpty).map(toRow(columns, _))
    Table(columns, rows)

  /** split の上限に -1 を渡すと、行末の空欄も空文字列として残る。 */
  private def toRow(columns: Vector[String], line: String): Row =
    Row(columns.zip(line.split(",", -1).toVector).toMap)
```

- `case class` と同じ名前の `object` は **コンパニオンオブジェクト** です。その型に関する「作り方」や定数を置く場所で、Java の `static` メンバーに当たります。`Table.load(...)` と、型の名前から呼べます
- `countMissing` の戻り値は `Vector[(String, Int)]`（組の並び）です。`Map` にすると順序の保証が無くなるので、列の順を保ったまま返します。Java 版が `LinkedHashMap` を使い、C# 版が順序付きの型を選んだところに当たる判断を、Scala では「そもそも `Map` にしない」で済ませました
- `columns.zip(values).toMap` は、列名の並びと値の並びを組にしてから `Map` にします。Java 版が `for` で `HashMap` に詰めたところが 1 行になります
- `toRow(columns, _)` の `_` は「引数をそのまま渡す」書き方で、`line => toRow(columns, line)` と同じです

### 行末の空欄の落とし穴

`split(",", -1)` の `-1` には理由があります。行の最後の列が空欄のテストを見てください。

```scala
  test("行の最後の列が空欄でも欠損値として読み込む") {
    val row = Table.load(writeCsv("0.1,0.2,0.3,,\n")).rows.head

    assert(row.number("花弁幅") === None)
    assert(row.text("種類") === "")
  }
```

Scala の `String.split` は Java の `String.split` そのものなので、上限を渡さないと **末尾に続く空の文字列を捨てます**。`"0.1,0.2,0.3,,"` は 5 列のつもりでも、`split(",")` では 3 つの要素しか返りません。`-1` を外すと、このテストが失敗します。

```text
[info] TableSpec:
[info] - CSV を読み込むと列名の並びを保ち、BOM は残らない
[info] - 空欄は欠損値の None として読み込む
[info] - 行の最後の列が空欄でも欠損値として読み込む *** FAILED ***
[info]   java.lang.IllegalArgumentException: 列がありません: 花弁幅
[info]   at machinelearning.chapter02.Row.text$$anonfun$1(Table.scala:17)
[info]   at scala.collection.immutable.Map$Map3.getOrElse(Map.scala:428)
[info]   at machinelearning.chapter02.Row.text(Table.scala:17)
[info]   at machinelearning.chapter02.Row.number(Table.scala:12)
[info]   ...
[info] Tests: succeeded 4, failed 1, canceled 0, ignored 0, pending 0
```

Java 版では、この落とし穴をテストより先に Error Prone（`StringSplitter`）が教えてくれました。Scala のコンパイラの警告にはこれに当たるものが無いので、テストで踏むほかありません。代わりに Scala では、失敗の形が Java 版と違います。Java 版は `ArrayIndexOutOfBoundsException` で落ちましたが、Scala 版は `zip` が短いほうに合わせるので、例外にならずに「列が足りない `Row`」ができ、`text` の「列がありません」のエラーになります。Kotlin の `split` は末尾の空の文字列を捨てないので、Kotlin 版ではこの問題自体が現れませんでした。

`-1` を書いたうえで、`toRow` のドキュメントコメントに理由を残しています。

### 列ごとの欠損値の数

```scala
  test("列ごとの欠損値の数を列の順に数える") {
    val table = Table.load(
      writeCsv("0.1,0.2,0.3,0.4,Iris-setosa\n,0.3,0.5,0.6,Iris-setosa\n,,0.7,0.8,Iris-virginica\n")
    )

    assert(
      table.countMissing === Vector("がく片長さ" -> 2, "がく片幅" -> 1, "花弁長さ" -> 0, "花弁幅" -> 0, "種類" -> 0)
    )
  }
```

`"がく片長さ" -> 2` は `("がく片長さ", 2)` と同じタプルの書き方です。`Vector` の `===` は要素と順序をそのまま比べるので、Java 版の `containsExactly` に当たる検査が等値比較になります。

| 観点 | 第 1 章の自作の読み込み | Kotlin DataFrame | Scala 版の `Table` |
|------|----------------------|------------------|------------------|
| BOM | 自分で取り除く | 取り除く | 自分で取り除く（`stripPrefix`） |
| 値の型 | 読み込むときに `Int` に変換 | 列ごとに推定する | 文字列のまま持ち、読むときに `number`・`text` を選ぶ |
| 空欄 | （無い前提） | `null` | `None` |
| 行末の空欄 | （無い前提） | 空欄として読む | `split(",", -1)` で残す |
| 変更できないこと | `Vector` | データフレームの操作が新しい値を返す | `case class` と不変の `Map`・`Vector` |

## 2.6 平均値で欠損値を補完する

### 平均値を求める

```scala
// src/test/scala/machinelearning/chapter02/PreprocessingSpec.scala
class PreprocessingSpec extends AnyFunSuite:
  private def sample(sepalLength: String, sepalWidth: String): Row =
    Row(Map("がく片長さ" -> sepalLength, "がく片幅" -> sepalWidth))

  test("欠損値を除いて列ごとの平均値を求める") {
    val rows = Vector(sample("0.1", "0.2"), sample("", "0.4"), sample("0.3", "0.9"))

    val means = Preprocessing.columnMeans(rows, Vector("がく片長さ", "がく片幅"))

    assert(means("がく片長さ") === 0.2 +- 1e-12)
    assert(means("がく片幅") === 0.5 +- 1e-12)
  }
```

```scala
  /** 欠損値を除いて、列ごとの平均値を求める。 */
  def columnMeans(rows: Vector[Row], columns: Vector[String]): Map[String, Double] =
    columns.map { column =>
      val values = rows.flatMap(_.number(column))
      column -> values.sum / values.size
    }.toMap
```

`rows.flatMap(_.number(column))` が、この章でいちばん Scala らしい 1 行です。`number` は `Option[Double]` を返しますが、`Option` は「要素が 0 個か 1 個のコレクション」としても扱えるので、`flatMap` に渡すと `None` の行が自然に消え、`Some` の中身だけが `Vector[Double]` として残ります。「欠損値を除く」と「値を取り出す」が 1 つの操作になります。

| 言語 | 欠損値を除いて値を取り出す |
|------|------------------------|
| Scala | `rows.flatMap(_.number(column))` |
| F# | `rows |> List.choose (fun r -> number r column)` |
| Java | `.map(row -> row.number(column)).filter(OptionalDouble::isPresent).mapToDouble(OptionalDouble::getAsDouble)` |
| Kotlin | `.mapNotNull { it[column] }` |

Java 版は `average()` が返す `OptionalDouble` を `orElseThrow()` で開きましたが、Scala 版は `values.sum / values.size` と素直に書いています。値が 1 つも無い列は `0.0 / 0` で `NaN` になりますが、この章で扱う列にはどれも値があるので、そこまでの作り込みはしていません。

### 補完して特徴量にする

補完に使う値は引数で受け取ります。補完の結果は `Row` ではなく `Features` です。

```scala
  test("欠損値を列ごとに指定した値で補完して特徴量にする") {
    val rows = Vector(sample("0.1", ""), sample("", "0.4"))
    val columns = Vector("がく片長さ", "がく片幅")

    val filled = Preprocessing.fillMissing(rows, columns, Map("がく片長さ" -> 0.2, "がく片幅" -> 0.5))

    assert(
      filled === Vector(Features(columns, Vector(0.1, 0.5)), Features(columns, Vector(0.2, 0.4)))
    )
  }

  test("元の行は変更しない") {
    val rows = Vector(sample("", "0.2"))

    val _ = Preprocessing.fillMissing(rows, Vector("がく片長さ", "がく片幅"), Map("がく片長さ" -> 0.2))

    assert(rows.head.isMissing("がく片長さ"))
  }
```

```scala
  /** 欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変更しない。 */
  def fillMissing(
      rows: Vector[Row],
      columns: Vector[String],
      values: Map[String, Double]
  ): Vector[Features] =
    rows.map { row =>
      Features(
        columns,
        columns.map(column =>
          row
            .number(column)
            .getOrElse(
              values.getOrElse(column, throw IllegalArgumentException(s"補完する値がありません: $column"))
            )
        )
      )
    }
```

`row.number(column).getOrElse(...)` は、値があればその値を、`None` なら補完する値を返します。Kotlin DataFrame の `fillNulls(列).with { 値 }` に当たる処理を、1 つのセルごとに書いています。`Row` も `Vector` も変更できないので、「元の行は変更しない」はこの設計から自然に満たされます。

「元の行は変更しない」のテストにある `val _ =` に注目してください。`fillMissing` の戻り値を使わずに呼んでいることを、読む人に明示するための書き方です。

ここは `-Wvalue-discard` が止めるわけではありません。実際に `val _ =` を外してコンパイルしてみると、警告も出ずに通ります。この指摘が出るのは、非 `Unit` の式が `Unit` の期待される位置に置かれて **値が暗黙に捨てられた** ときだけで、ブロックの途中に置いた式そのものは対象外です（それを見たいときは `-Wnonunit-statement` を足します）。実際に止まる例は 2.10 節で見ます。

### Features は case class のままでよい

`Features` は、`Row` や `Table` と同じ `case class` です。

```scala
// src/main/scala/machinelearning/chapter02/Preprocessing.scala
/** 補完が済んだ 1 行分の特徴量。欠損値を持てないので、補完する前の行をモデルに渡すことはできない。 値は Vector なので、case class
  * の等価判定がそのまま値の比較になる（Java・C# では配列を包む工夫が要る）。
  */
case class Features(columns: Vector[String], values: Vector[Double]):
  require(columns.size == values.size, "列名と値の数が違います")

  /** 列名で値を読む。 */
  def value(column: String): Double =
    val index = columns.indexOf(column)
    if index < 0 then throw IllegalArgumentException(s"列がありません: $column") else values(index)
```

ここが Java 版・C# 版といちばん差が出たところです。Java 版は、第 3 章で Tribuo の `ArrayExample` に渡すために値を `double[]` で持ちたかったのですが、record の成分に配列を使うと自動生成される `equals` が **中身ではなく同じ配列かどうか** を比べてしまいます（Error Prone の `ArrayRecordComponent`）。そこで record をやめ、配列を `clone()` で写して持ち、`equals`・`hashCode`・`toString` を自分で書く 50 行ほどのクラスにしました。C# 版も同じ理由で record の既定の等価判定を上書きしています。

Scala では、値を `Vector[Double]` で持てば `case class` のままで済みます。`Vector` は不変なコレクションで、`equals` は要素を順に比べるので、`case class` が自動生成する等価判定がそのまま「値の比較」になります。写しを取る必要もありません。

```scala
  test("列名と値の数が違えばエラーになる") {
    assertThrows[IllegalArgumentException](Features(Vector("a", "b"), Vector(0.1)))
  }

  test("列名と値が同じなら等しい（Vector は値で比べられる）") {
    assert(Features(Vector("a"), Vector(0.1)) === Features(Vector("a"), Vector(0.1)))
  }
```

- `require(条件, メッセージ)` は、条件が偽なら `IllegalArgumentException` を投げます。`case class` の本体に書けるので、Java 版のコンパクトコンストラクタに当たる検査が 1 行です
- Java 版は「値の配列を写して持ち、渡した配列を後から変えても影響を受けない」というテストも必要でしたが、`Vector` は変更できないので、Scala 版にはそのテストがありません。書きようがないことは、テストで守らなくてよくなります

第 3 章で Tribuo に渡すときは `values.toArray` で配列にします。「Scala の側は不変なコレクションで持ち、境界で配列に変える」という分担です。

## 2.7 特徴量と正解ラベルに分ける

```scala
  test("特徴量の列と正解ラベルの列に分ける") {
    val table = Table(
      Vector("がく片長さ", "花弁幅", "種類"),
      Vector(
        Row(Map("がく片長さ" -> "0.1", "花弁幅" -> "0.4", "種類" -> "Iris-setosa")),
        Row(Map("がく片長さ" -> "0.5", "花弁幅" -> "0.8", "種類" -> "Iris-virginica"))
      )
    )

    val (columns, rows, target) = Preprocessing.splitFeaturesAndTarget(table, "種類")

    assert(columns === Vector("がく片長さ", "花弁幅"))
    assert(rows === table.rows)
    assert(target === Vector("Iris-setosa", "Iris-virginica"))
  }
```

```scala
  /** 正解ラベルの列を取り出し、残りの列を特徴量の列にする。 */
  def splitFeaturesAndTarget(
      table: Table,
      target: String
  ): (Vector[String], Vector[Row], Vector[String]) =
    (table.columns.filterNot(_ == target), table.rows, table.rows.map(_.text(target)))
```

3 つの値をまとめて返すので、戻り値は **タプル** です。Java 版は `FeaturesAndTarget` という record を、C# 版は名前付きのタプルを作りました。Scala でも `case class` にできますが、この関数はすぐ後の `prepareIris` で使うだけなので、第 1 章の `splitFeaturesAndLabels` と同じくタプルのままにしています。受け取る側は `val (columns, rows, target) = ...` と 1 行で分解できるので、要素の意味は変数名で伝わります。

Kotlin 版は `df.remove(target)` で正解ラベルの列を除いたデータフレームを作りました。Scala 版の `Row` は列名で引く形なので、行から列を消す必要はありません。「どの列を特徴量として読むか」を列名の `Vector` で持ち、行はそのまま渡します。

## 2.8 訓練データとテストデータに分ける

### 分割結果を表す型と仮実装

```scala
/** 訓練データとテストデータ。X は特徴量の型、T は正解ラベルの型。 */
case class TrainTestSplit[X, T](
    xTrain: Vector[X],
    xTest: Vector[X],
    tTrain: Vector[T],
    tTest: Vector[T]
)
```

Java 版と同じく、特徴量の型も正解ラベルの型も型引数にしています。2.9 節で見るように、補完する前の `Row` のまま分けてから、訓練データの平均値で補完して `Features` にするためです。第 7 章からは、価格のような数値の正解ラベルも同じ関数で分けます。

0 から始まる番号を振ったデータで、件数だけを確かめます。

```scala
  private val numbers = (0 until 10).toVector
  private val labels = numbers.map(i => s"label$i")

  test("テストデータの割合どおりの件数に分ける") {
    val split = Preprocessing.splitTrainTest(numbers, labels, 0.3, 0)

    assert((split.xTrain.size, split.xTest.size) === (7, 3))
    assert((split.tTrain.size, split.tTest.size) === (7, 3))
  }
```

型引数のおかげで、テストでは特徴量に `Int` の番号をそのまま使えます。Kotlin 版はテスト用に 1 列のデータフレームを作りましたが、Scala 版では `Vector` だけで済みます。2 つの件数をタプルにして一度に比べているのは、失敗したときに両方の値が見えるようにするためです。

仮実装では、先頭の 7 件を訓練データにします。

```scala
  def splitTrainTest[X, T](
      x: Vector[X],
      t: Vector[T],
      testSize: Double,
      seed: Long
  ): TrainTestSplit[X, T] =
    require(x.size == t.size, "特徴量と正解ラベルの件数が違います")
    val pairs = x.zip(t)
    val trainCount = 7
    val (train, test) = pairs.splitAt(trainCount)
    TrainTestSplit(train.map(_._1), test.map(_._1), train.map(_._2), test.map(_._2))
```

特徴量と正解ラベルを `zip` で組にしてから分けるので、「対応を保つ」は自動的に満たされます。Java 版は `zip` が無いので「行の位置のリストを並べ替えて、その位置で両方から取り出す」と書きました。`splitAt` は、指定した位置で 2 つに分けた組を返します。

### 三角測量: 件数を一般化する

```scala
  test("件数が変わってもテストデータの割合どおりに分ける") {
    val twenty = (0 until 20).toVector

    val split = Preprocessing.splitTrainTest(twenty, twenty, 0.25, 0)

    assert((split.xTrain.size, split.xTest.size) === (15, 5))
  }
```

```text
[info] - テストデータの割合どおりの件数に分ける
[info] - 件数が変わってもテストデータの割合どおりに分ける *** FAILED ***
[info]   (7, 13) did not equal (15, 5) (PreprocessingSpec.scala:77)
[info]   Analysis:
[info]   Tuple2(_1: 7 -> 15, _2: 13 -> 5)
```

2 つ目の例で、7 がベタ書きの値だったことが露わになりました。テストデータの件数を切り上げて求めます。

```scala
    val trainCount = pairs.size - math.ceil(pairs.size * testSize).toInt
```

`math.ceil` の戻り値は `Double` なので、`.toInt` で整数にしてから引きます。

### 三角測量: 並び順に頼らない分け方にする

ほかの言語版と同じく、分け方の性質を表すテストを重ねます。

```scala
  test("すべての行を重複なく訓練データとテストデータのどちらかに入れる") {
    val split = Preprocessing.splitTrainTest(numbers, labels, 0.3, 0)

    assert(split.xTrain.intersect(split.xTest).isEmpty)
    assert((split.xTrain ++ split.xTest).sorted === numbers)
  }

  test("特徴量と正解ラベルの対応を保ったまま分ける") {
    val split = Preprocessing.splitTrainTest(numbers, labels, 0.3, 0)

    assert(split.tTrain === split.xTrain.map(i => s"label$i"))
    assert(split.tTest === split.xTest.map(i => s"label$i"))
  }

  test("同じシードなら同じ分け方になる") {
    assert(
      Preprocessing.splitTrainTest(numbers, labels, 0.3, 42).tTest
        === Preprocessing.splitTrainTest(numbers, labels, 0.3, 42).tTest
    )
  }

  test("シードが違えば違う分け方になる") {
    assert(
      Preprocessing.splitTrainTest(numbers, labels, 0.3, 0).tTest
        !== Preprocessing.splitTrainTest(numbers, labels, 0.3, 1).tTest
    )
  }
```

Scala のコレクションには `intersect`（共通の要素）と `++`（連結）があるので、Java 版が `HashSet` を作って AssertJ の専用の検査を使ったところが、標準のメソッドと `===` で書けます。

```text
[info] - テストデータの割合どおりの件数に分ける
[info] - 件数が変わってもテストデータの割合どおりに分ける
[info] - すべての行を重複なく訓練データとテストデータのどちらかに入れる
[info] - 特徴量と正解ラベルの対応を保ったまま分ける
[info] - 同じシードなら同じ分け方になる
[info] - シードが違えば違う分け方になる *** FAILED ***
[info]   Vector("label7", "label8", "label9") equaled Vector("label7", "label8", "label9") (PreprocessingSpec.scala:102)
[info] Tests: succeeded 13, failed 1, canceled 0, ignored 0, pending 0
```

Python 版・Java 版と同じく、先頭から順に分けるだけでは「シードが違えば違う分け方になる」だけが失敗します。どのシードでも最後の 3 件がテストデータになるためです。

### Green: シード付きの乱数で並べ替える

```scala
  /** シードを使って Fisher-Yates のシャッフルで並べ替える。Java 版（Collections.shuffle）と同じ
    * 乱数・同じ手順なので、同じシードなら同じ並びになる。元のリストは変更しない。
    */
  def shuffle[A](items: Vector[A], seed: Long): Vector[A] =
    val random = Random(seed)
    val array = items.toArray[Any]
    for i <- array.length - 1 to 1 by -1 do
      val j = random.nextInt(i + 1)
      val tmp = array(i)
      array(i) = array(j)
      array(j) = tmp
    array.toVector.map(_.asInstanceOf[A])
```

`scala.util.Random.shuffle(items)` と書けば 1 行で済みます。それでもこう書いたのは、**Java 版と同じ乱数列・同じ手順で並べ替えるため** です。

- `java.util.Random(seed)` は、シードが同じなら同じ乱数列を返す生成器です。Java の `Collections.shuffle(list, random)` は、末尾から先頭へ向かって `random.nextInt(i + 1)` の位置と交換していく Fisher-Yates のシャッフルです。その手順をそのまま書き写しました
- Scala では `new` を書かずに `Random(seed)` と呼べます（Scala 3 の **universal apply methods**）
- `for i <- array.length - 1 to 1 by -1 do` は、`array.length - 1` から 1 まで 1 ずつ減らす繰り返しです
- 並べ替えは可変な配列の上で行い、最後に `toVector` で不変なコレクションに戻します。関数の外からは「元の `Vector` を受け取って新しい `Vector` を返す」ようにしか見えません。可変なものを関数の中に閉じ込める、よくある書き方です
- `toArray[Any]` としているのは、型引数 `A` の具体的な型が実行時には分からない（型消去）ためです。`Any` の配列で扱い、戻すときに `asInstanceOf[A]` でキャストします

```scala
    val pairs = shuffle(x.zip(t), seed)
```

並べ替えるのは「特徴量と正解ラベルの組」なので、対応は崩れません。

### Java 版と同じ並びになることを確かめる

同じ乱数・同じ手順にした目的を、テストで固定します。

```scala
  test("Java 版と同じ Fisher-Yates の並べ替えになる") {
    // Java 版（Collections.shuffle(positions, new Random(0))）で実測した並び
    assert(Preprocessing.shuffle(numbers, 0) === Vector(4, 8, 9, 6, 3, 5, 2, 1, 7, 0))
  }
```

0 から 9 の 10 個をシード 0 で並べ替えると、Java 版でも Scala 版でも `[4, 8, 9, 6, 3, 5, 2, 1, 7, 0]` になります。`java.util.Random` は JDK の仕様で乱数列が決まっているので、同じ JVM の上なら言語を変えても同じ値が出ます。

この 1 本のテストが、以降の章で数値を Java 版と突き合わせる根拠になります。逆に、`scala.util.Random.shuffle` に変えたり、Fisher-Yates を先頭から回すように書き換えたりすると、このテストが即座に落ちます。

数値の正解ラベルも同じ関数で分けられることも確かめておきます。

```scala
  test("数値の正解ラベルも特徴量との対応を保ったまま分ける") {
    val numeric = numbers.map(_ * 0.5)

    val split = Preprocessing.splitTrainTest(numbers, numeric, 0.3, 0)

    assert(split.tTest === split.xTest.map(_ * 0.5))
  }
```

## 2.9 前処理をまとめる

```scala
  /** 正解ラベルの列 */
  val Target = "種類"

  /** iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。 */
  def prepareIris(csvFile: Path, testSize: Double, seed: Long): TrainTestSplit[Features, String] =
    val (columns, rows, target) = splitFeaturesAndTarget(Table.load(csvFile), Target)
    val split = splitTrainTest(rows, target, testSize, seed)
    val means = columnMeans(split.xTrain, columns)
    TrainTestSplit(
      fillMissing(split.xTrain, columns, means),
      fillMissing(split.xTest, columns, means),
      split.tTrain,
      split.tTest
    )
```

型の流れを追うと、補完の前後の区別がはっきり見えます。

1. `splitTrainTest` は `Row`（欠損しうる）のまま分けて `TrainTestSplit[Row, String]` を返す
2. `columnMeans` は訓練データの `Row` だけから平均値を求める
3. `fillMissing` がその平均値で訓練データとテストデータの両方を補完し、`TrainTestSplit[Features, String]`（欠損値を持てない）にする

戻り値の型が `TrainTestSplit[Features, String]` なので、`prepareIris` を通ったデータに欠損値が残っていないことは型が保証します。

`prepareIris` は、すでにテストで守られている関数を組み合わせるだけなので、実装を書いてから実データで出力を確かめ、テストで固定しました。ここは Red を経ていません。

## 2.10 実データで前処理の結果を表示する

### 実データのテスト

```scala
// src/test/scala/machinelearning/chapter02/IrisDataSpec.scala
class IrisDataSpec extends AnyFunSuite:
  private val csvFile = Paths.get(DataDir.current(), "iris.csv")

  private def requireData(): org.scalatest.Assertion =
    assume(Files.exists(csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）")

  test("実データの列ごとの欠損値の数を数える") {
    requireData(): Unit

    assert(
      Table.load(csvFile).countMissing
        === Vector("がく片長さ" -> 2, "がく片幅" -> 1, "花弁長さ" -> 2, "花弁幅" -> 2, "種類" -> 0)
    )
  }

  test("実データを 105 件と 45 件に分けて欠損値を補完する") {
    requireData(): Unit

    val split = Preprocessing.prepareIris(csvFile, 0.3, 0)

    assert((split.xTrain.size, split.xTest.size) === (105, 45))
    assert((split.tTrain.size, split.tTest.size) === (105, 45))
  }
```

第 1 章と同じく、学習データが無ければ `assume` でテストをキャンセルします。`requireData(): Unit` と型を書いているのは `-Wvalue-discard` のためです。型注釈を外すと、`assume` が返す `Assertion` を黙って捨てることになり、コンパイルが止まります。

```text
[error] -- [E175] Potential Issue Error: .../chapter02/IrisDataSpec.scala:12:4
[error] 12 |    assume(Files.exists(csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）")
[error]    |    ^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
[error]    |discarded non-Unit value of type org.scalatest.compatible.Assertion. Add `: Unit` to discard silently.
[error] one error found
[error] (Test / compileIncremental) Compilation failed
```

エラーメッセージが勧めるとおり、ヘルパーは `Assertion` をそのまま返し、呼ぶ側で `: Unit` と書いて「ここでは意図して捨てている」と示します。

Kotlin 版の実データのテストは、補完した後の欠損値の数が 0 であることも確かめていました。Scala 版の `Features` は欠損値を持てないので、その検査は型が代わりに担います。数えようにも、`Features` には欠損値を数える方法がありません。

### Java 版との突き合わせ

2.8 節で並べ替えが一致することを確かめたので、実データでも数値が一致するはずです。訓練データの平均値と、テストデータの先頭のラベルを固定します。

```scala
  test("訓練データの平均値は Java 版と同じになる（同じ乱数と同じ分け方）") {
    requireData(): Unit
    val (columns, rows, target) =
      Preprocessing.splitFeaturesAndTarget(Table.load(csvFile), Preprocessing.Target)
    val split = Preprocessing.splitTrainTest(rows, target, 0.3, 0)

    val means = Preprocessing.columnMeans(split.xTrain, columns)

    // Java 版（java.util.Random と同じ Fisher-Yates）で実測した値
    assert(means("がく片長さ") === 0.4215384615384616 +- 1e-12)
    assert(means("がく片幅") === 0.43826923076923074 +- 1e-12)
    assert(means("花弁長さ") === 0.47644230769230766 +- 1e-12)
    assert(means("花弁幅") === 0.4475 +- 1e-12)
    assert(split.tTest.take(3) === Vector("Iris-setosa", "Iris-virginica", "Iris-setosa"))
  }
```

このテストは通ります。訓練データ 105 件の平均値が Double の桁いっぱいまで Java 版と一致し、テストデータの先頭 3 件のラベルも同じです。つまり、**どの行が訓練データに入り、どの行がテストデータに入るかまで Java 版と同じ** ということです。

学習データの行そのものは記事にもテストにも書けないので（ライセンスの都合でリポジトリにコミットできません）、こうして「実データから計算した値」を期待値にしています。データが無い環境ではこのテストはキャンセルされます。

### 結果を表示する

章ごとの実行は、第 1 章と同じく `Main.run(print: String => Unit)` の形です。表示の関数を引数で受け取るので、テストから出力をそのまま集められます。

```scala
// src/main/scala/machinelearning/chapter02/Main.scala
/** アヤメのデータの前処理の結果を表示する。 */
object Main:
  private val TestSize = 0.3
  private val Seed = 0L

  def run(print: String => Unit): Unit =
    val csvFile = Paths.get(DataDir.current(), "iris.csv")
    val table = Table.load(csvFile)
    val split = Preprocessing.prepareIris(csvFile, TestSize, Seed)
    print(s"データ件数: ${table.rows.size}")
    print("欠損値の数: " + table.countMissing.map((column, count) => s"$column=$count").mkString(", "))
    print(s"訓練データ: ${split.xTrain.size} 件, テストデータ: ${split.xTest.size} 件")
    print("特徴量: " + split.xTrain.head.columns.mkString(", "))
```

`map((column, count) => ...)` は、タプルの要素をそのまま引数の名前で受け取る書き方です（Scala 3）。Java 版が `entry.getKey()`・`entry.getValue()` と書いたところが読みやすくなります。

`machinelearning.Main` の対応表に `"chapter02"` を足すと、章を選んで実行できます。

```scala
  private val chapters: Map[String, (String => Unit) => Unit] = Map(
    "chapter01" -> machinelearning.chapter01.Main.run,
    "chapter02" -> machinelearning.chapter02.Main.run
  )
```

出力はテストで固定します。

```scala
  test("実行すると前処理の結果を表示する") {
    requireData(): Unit
    val output = Vector.newBuilder[String]

    Main.run(output += _)

    assert(
      output.result() === Vector(
        "データ件数: 150",
        "欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0",
        "訓練データ: 105 件, テストデータ: 45 件",
        "特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅"
      )
    )
  }
```

`Vector.newBuilder[String]` に `output += _` を渡すだけで、表示された行が順に集まります。Java 版は標準出力を差し替える `StdoutCapture` を用意しましたが、表示の関数を引数にしておけばその仕掛けは要りません。

```bash
sbt "run chapter02"
```

```text
データ件数: 150
欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
訓練データ: 105 件, テストデータ: 45 件
特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅
```

件数と欠損値の数は乱数に関係しないので、どの言語版でも同じです。

```bash
sbt test
```

第 1〜3 章を合わせたテストは 53 件すべて通ります。データが無い環境（`ML_DATA_DIR=/nonexistent sbt test`）では、実データのテスト 10 件（第 1 章 3 件・第 2 章 4 件・第 3 章 3 件）がキャンセルされ、残りの 43 件が通ります。

**TODO リスト**:

- [x] iris.csv を読み込む
  - [x] BOM 付き CSV の列名に BOM が残らない
  - [x] 空欄を欠損値（`None`）として読み込む
  - [x] 行末の空欄も欠損値として読み込む
  - [x] 無い列を読み出すと列名を示すエラーになる
- [x] 列ごとの欠損値の数を数える
- [x] 列ごとの平均値を求める
- [x] 欠損値を補完する
  - [x] 列ごとに指定した値で補完する
  - [x] 元のデータは変更しない
- [x] 特徴量と正解ラベルに分ける
- [x] 訓練データとテストデータに分ける
  - [x] テストデータの割合どおりの件数に分ける
  - [x] すべての行を重複なくどちらかに入れる
  - [x] 特徴量と正解ラベルの対応を保つ
  - [x] 同じシードなら同じ分け方になる
  - [x] シードが違えば違う分け方になる
  - [x] Java 版と同じ並べ替えになる
- [x] 訓練データの平均値で両方を補完する
- [x] 実データで前処理の結果を表示する

## 2.11 リファクタリング

整形と静的解析を通します。

```bash
sbt "scalafmtAll; scalafmtCheckAll; test"
```

第 1 章で入れた `-Wunused:all -Wvalue-discard -Xfatal-warnings` は、この章では 1 か所で効きました。

| 指摘 | 最初に書いたもの | 直したもの |
|------|---------------|-----------|
| `-Wvalue-discard`（テスト） | ヘルパーの戻り値を `Unit` にして `assume` を呼ぶ | `Assertion` を返し、呼ぶ側で `requireData(): Unit` |
| 読みやすさ（テスト） | `Preprocessing.fillMissing(rows, ...)` と書き捨てる | `val _ = Preprocessing.fillMissing(rows, ...)`（`-Wvalue-discard` の対象外だが、意図を明示する） |

Java 版で Error Prone が止めた 3 つの指摘（配列を成分に持つ record、`split` の末尾の空欄、文字列の連結）は、Scala では現れませんでした。1 つ目は `Vector` を使うので問題自体が起きず、2 つ目はテストで踏み、3 つ目は複数行の文字列を書く場面がテストに無かったためです。ツールが違えば、教えてくれる落とし穴も変わります。

## 2.12 可視化について

Scala 版には Notebook の節を設けません。欠損値の分布や、品種ごとの特徴量の散布図は、[Python 版の第 2 章](../python/02-data-preprocessing-and-triangulation.md) と [Kotlin 版の第 2 章](../kotlin/02-data-preprocessing-and-triangulation.md) の「Notebook で探索する」の節を参照してください。訓練データに入る行は言語ごとに違いますが（Scala 版は Java 版と同じです）、「`Iris-setosa` は花弁長さ・花弁幅がほかの 2 品種よりはっきり小さい」という傾向は同じで、第 3 章の決定木はこの傾向を自動で見つけます。

<details>
<summary>この章の完成コード（src/main/scala/machinelearning/chapter02/Table.scala）</summary>

```scala
package machinelearning.chapter02

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters.*

/** CSV の 1 行。セルの文字列を列名で引けるように持つ。空欄は欠損値として扱う。 */
case class Row(cells: Map[String, String]):

  /** 数値の列を読む。空欄なら None を返す。 */
  def number(column: String): Option[Double] =
    val cell = text(column)
    if cell.trim.isEmpty then None else Some(cell.toDouble)

  /** 文字列の列を読む。 */
  def text(column: String): String =
    cells.getOrElse(column, throw IllegalArgumentException(s"列がありません: $column"))

  /** セルが空欄かどうか。 */
  def isMissing(column: String): Boolean = text(column).trim.isEmpty

/** 列名の並びと行のリスト。データフレームのライブラリの代わりに使う、変更できない表。 */
case class Table(columns: Vector[String], rows: Vector[Row]):

  /** 列ごとの欠損値の数を、列の順に並べて返す。 */
  def countMissing: Vector[(String, Int)] =
    columns.map(column => column -> rows.count(_.isMissing(column)))

object Table:
  private val Bom = "\uFEFF"

  /** BOM 付きの UTF-8 の CSV を読み込む。 */
  def load(csvFile: Path): Table =
    val lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8).asScala.toVector
    val columns = lines.head.stripPrefix(Bom).split(",", -1).toVector
    val rows = lines.tail.filter(_.trim.nonEmpty).map(toRow(columns, _))
    Table(columns, rows)

  /** split の上限に -1 を渡すと、行末の空欄も空文字列として残る。 */
  private def toRow(columns: Vector[String], line: String): Row =
    Row(columns.zip(line.split(",", -1).toVector).toMap)
```

</details>

<details>
<summary>この章の完成コード（src/main/scala/machinelearning/chapter02/Preprocessing.scala）</summary>

```scala
package machinelearning.chapter02

import java.nio.file.Path
import java.util.Random

/** 補完が済んだ 1 行分の特徴量。欠損値を持てないので、補完する前の行をモデルに渡すことはできない。 値は Vector なので、case class
  * の等価判定がそのまま値の比較になる（Java・C# では配列を包む工夫が要る）。
  */
case class Features(columns: Vector[String], values: Vector[Double]):
  require(columns.size == values.size, "列名と値の数が違います")

  /** 列名で値を読む。 */
  def value(column: String): Double =
    val index = columns.indexOf(column)
    if index < 0 then throw IllegalArgumentException(s"列がありません: $column") else values(index)

/** 訓練データとテストデータ。X は特徴量の型、T は正解ラベルの型。 */
case class TrainTestSplit[X, T](
    xTrain: Vector[X],
    xTest: Vector[X],
    tTrain: Vector[T],
    tTest: Vector[T]
)

/** アヤメのデータの前処理。 */
object Preprocessing:

  /** 正解ラベルの列 */
  val Target = "種類"

  /** 欠損値を除いて、列ごとの平均値を求める。 */
  def columnMeans(rows: Vector[Row], columns: Vector[String]): Map[String, Double] =
    columns.map { column =>
      val values = rows.flatMap(_.number(column))
      column -> values.sum / values.size
    }.toMap

  /** 欠損値を列ごとに指定した値で補完し、特徴量にする。元の行は変更しない。 */
  def fillMissing(
      rows: Vector[Row],
      columns: Vector[String],
      values: Map[String, Double]
  ): Vector[Features] =
    rows.map { row =>
      Features(
        columns,
        columns.map(column =>
          row
            .number(column)
            .getOrElse(
              values.getOrElse(column, throw IllegalArgumentException(s"補完する値がありません: $column"))
            )
        )
      )
    }

  /** 正解ラベルの列を取り出し、残りの列を特徴量の列にする。 */
  def splitFeaturesAndTarget(
      table: Table,
      target: String
  ): (Vector[String], Vector[Row], Vector[String]) =
    (table.columns.filterNot(_ == target), table.rows, table.rows.map(_.text(target)))

  /** シードを使って Fisher-Yates のシャッフルで並べ替える。Java 版（Collections.shuffle）と同じ
    * 乱数・同じ手順なので、同じシードなら同じ並びになる。元のリストは変更しない。
    */
  def shuffle[A](items: Vector[A], seed: Long): Vector[A] =
    val random = Random(seed)
    val array = items.toArray[Any]
    for i <- array.length - 1 to 1 by -1 do
      val j = random.nextInt(i + 1)
      val tmp = array(i)
      array(i) = array(j)
      array(j) = tmp
    array.toVector.map(_.asInstanceOf[A])

  /** 並べ替えてから、テストデータの割合（切り上げ）で訓練データとテストデータに分ける。 */
  def splitTrainTest[X, T](
      x: Vector[X],
      t: Vector[T],
      testSize: Double,
      seed: Long
  ): TrainTestSplit[X, T] =
    require(x.size == t.size, "特徴量と正解ラベルの件数が違います")
    val pairs = shuffle(x.zip(t), seed)
    val trainCount = pairs.size - math.ceil(pairs.size * testSize).toInt
    val (train, test) = pairs.splitAt(trainCount)
    TrainTestSplit(train.map(_._1), test.map(_._1), train.map(_._2), test.map(_._2))

  /** iris.csv を読み込み、分割してから訓練データの平均値で両方を補完する。 */
  def prepareIris(csvFile: Path, testSize: Double, seed: Long): TrainTestSplit[Features, String] =
    val (columns, rows, target) = splitFeaturesAndTarget(Table.load(csvFile), Target)
    val split = splitTrainTest(rows, target, testSize, seed)
    val means = columnMeans(split.xTrain, columns)
    TrainTestSplit(
      fillMissing(split.xTrain, columns, means),
      fillMissing(split.xTest, columns, means),
      split.tTrain,
      split.tTest
    )
```

</details>

`Main.scala` は本文に載せたものが完成版です（import 文を省略しています）。

## 2.13 まとめ

この章では、データフレームのライブラリを使わずに、欠損値を含むデータを前処理し、訓練データとテストデータに分けました。

1. **case class と不変のコレクション** — `Row`・`Table`・`Features` を `case class` と `Map`・`Vector` で書いた。写しを取る手当ても、変更を防ぐ工夫も要らない
2. **`Vector` は値で比べられる** — Java 版・C# 版が「配列を包んで `equals` を自分で書く」ことになったところが、`case class` の既定の等価判定で済んだ
3. **`Option` で欠損値を表す** — `flatMap` に渡すだけで「欠損値を除いて値を取り出す」が 1 つの操作になった。F# の `option` と同じ形で、Java の `OptionalDouble`・C# の `double?` に当たる
4. **補完の前後を型で分ける** — 欠損しうる `Row` と、欠損値を持てない `Features` を分け、`prepareIris` の戻り値の型で「欠損値が残っていない」ことを保証した
5. **仮実装と三角測量** — ベタ書きの 7 件から件数の計算へ、先頭から順に分ける実装からシード付きの並べ替えへ、2 つ目の例が出たところで一般化した
6. **Java 版と同じ乱数を選ぶ** — 短く書ける `scala.util.Random.shuffle` ではなく `java.util.Random` と Fisher-Yates にして、並べ替えの結果と訓練データの平均値が Java 版と一致することをテストで固定した
7. **`-Wvalue-discard` が値を捨てさせない** — `assume` の結果も `fillMissing` の結果も、捨てるなら捨てると書かないとコンパイルが通らない

次の章では、Scala 3 の `enum` で決定木を自作し、`match` の網羅性をコンパイラに検査させながら、JVM の機械学習ライブラリ Tribuo の決定木と結果を突き合わせます。
