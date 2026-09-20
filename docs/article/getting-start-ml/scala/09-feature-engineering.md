---
type: Article
title: "第 9 章: 特徴量エンジニアリング"
description: "ダミー変数・標準化・多項式特徴量・外れ値検出・Shift_JIS の表の結合を Scala の TDD で自作し、特徴量の組み合わせごとの決定係数を測って、Tribuo の標準化との違い（不偏標準偏差）を確かめる。"
tags: [article,getting-start-ml,scala]
status: draft
generated: { by: claude-code/claude-opus-5, at: 2026-09-20T03:10:00Z }
---

# 第 9 章: 特徴量エンジニアリング

## 9.1 はじめに

モデルの性能は、アルゴリズムよりも「どんな特徴量を渡すか」で大きく変わることがあります。元のデータから、モデルが学びやすい特徴量を作り出す作業を **特徴量エンジニアリング** と呼びます。

この章では、ボストンの住宅価格データを題材に、次の 5 つの技法を TDD で自作します。

- カテゴリ値をダミー変数（0 と 1 の列）にする
- 特徴量を標準化する
- 2 乗の項と交互作用の項（多項式特徴量）を作る
- 外れ値を検出する
- 別の表を結合して特徴量を増やす

標準化は Tribuo の `MeanStdDevTransformation` と突き合わせます。最後に、作った特徴量で線形回帰の決定係数がどう変わるかを実データで測ります。

[Python 版の第 9 章](../python/09-feature-engineering.md) と同じ TODO リストで進め、同じ JVM・同じ Tribuo を使う [Java 版](../java/09-feature-engineering.md)・[Kotlin 版](../kotlin/09-feature-engineering.md) と、関数型の [F# 版](../fsharp/09-feature-engineering.md) を対比します。Kotlin 版は Kotlin DataFrame の `convert`・`add`・`innerJoin`・`groupBy` で表を操作しました。Scala 版は Java 版と同じく、データフレームのライブラリを使わずに第 2 章の `Table`・`Row`・`Features` と不変コレクションで書きます。

## 9.2 題材とデータ

学習データの入手と配置は [第 1 章](01-machine-learning-and-first-test.md) の「題材とデータ」を参照してください。この章では 3 つのファイルを使います。

| ファイル | 内容 | 区切り文字 | 文字コード |
|---------|------|----------|-----------|
| `Boston.csv` | 地域ごとの住宅価格。100 件、14 列。CRIME は `high`・`low`・`very_low` のカテゴリ値、NOX と RAD に欠損値 | カンマ | ASCII の範囲だけ（BOM なし） |
| `bike.tsv` | 自転車シェアの日ごとの利用者数（`cnt`）と天気 ID（`weather_id`）。731 件 | **タブ** | ASCII の範囲だけ（BOM なし） |
| `weather.csv` | 天気 ID と天気の名前（晴れ・曇り・雨）。3 件 | カンマ | **Shift_JIS**（UTF-8 としては読めない） |

第 2 章の `Table.load` は「BOM 付きの UTF-8 のカンマ区切り」を読むメソッドです。`Boston.csv` は BOM が無くても読めるのでそのまま使い、`bike.tsv` と `weather.csv` は、この章で区切り文字と文字コードを指定できる読み込みを足します（第 2 章の `Table` は変更しません）。

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
  - [ ] 交互作用の項を加える
  - [ ] 使う項を選ぶ
- [ ] 外れ値を検出する
  - [ ] 分位数を線形補間で求める
  - [ ] 四分位範囲（IQR）で外れ値を判定する
  - [ ] 訓練データから外れ値の行を取り除く
- [ ] 表を結合して特徴量を増やす
  - [ ] 区切り文字と文字コードを指定して読み込む
  - [ ] 天気 ID で 2 つの表を結合する
  - [ ] 天気ごとの平均利用者数を求める
- [ ] 特徴量の組み合わせごとに決定係数を比べる

この章のコードは `machinelearning.chapter09` パッケージに置き、技法ごとにファイルを分けます。

| ファイル | 役割 |
|---------|------|
| `Dummies.scala` | ダミー変数 |
| `Standardizer.scala` | 標準化（case class） |
| `TribuoStandardization.scala` | Tribuo の標準化の呼び出し |
| `PolynomialFeatures.scala` | 多項式特徴量と項（`Term`） |
| `Outliers.scala` | 外れ値の検出 |
| `DelimitedFiles.scala`・`BikeWeather.scala` | 区切り文字・文字コードを指定した読み込みと、表の結合 |
| `LinearModel.scala`・`Boston.scala` | 正規方程式による線形回帰、決定係数（`Scores`）、Boston データの前処理 |

## 9.4 カテゴリ値をダミー変数にする

### カテゴリを求める

CRIME のようなカテゴリ値は、そのままでは線形回帰に渡せません。カテゴリごとに「その値なら 1、それ以外は 0」の列を作ります。3 つのカテゴリなら 2 列あれば区別できる（2 列とも 0 なら残りの 1 つ）ので、Python 版の `pd.get_dummies(drop_first=True)` と同じく、辞書順で先頭のカテゴリを除きます。

```scala
test("先頭を除いたカテゴリを辞書順に返す") {
  assert(
    Dummies.categories(Vector("low", "high", "very_low", "low")) === Vector("low", "very_low")
  )
}

test("欠損値（空欄）はカテゴリに数えない") {
  assert(Dummies.categories(Vector("low", "", "high")) === Vector("low"))
}
```

第 2 章の `Row` は空欄を空文字列のまま持つので、`trim.nonEmpty` で除きます（Kotlin 版は欠損値を `null` で表し `filterNotNull()` で除きました）。

```scala
/** 欠損値（空欄）を除いたカテゴリを辞書順に並べ、先頭を除いて返す（pandas の drop_first=True と同じ）。 */
def categories(values: Vector[String]): Vector[String] =
  values.filter(_.trim.nonEmpty).distinct.sorted.drop(1)
```

Java 版の `stream().filter(...).distinct().sorted().skip(1).toList()` と、やることは同じです。Scala の `Vector` はコレクションそのものが `filter`・`distinct`・`sorted`・`drop` を持つので、ストリームに変換して戻す往復が要りません。`sorted` が引数なしで書けるのは、`String` に `Ordering` の given があるからです。

### 表に列を加える

ダミー変数の列は、表の末尾に「列名_カテゴリ」という名前で加えます。

```scala
test("カテゴリごとに 0 と 1 の列を作り元の列を取り除く") {
  val encoded =
    Dummies.encode(crimeTable("low", "high", "very_low"), "CRIME", Vector("low", "very_low"))

  assert(encoded.columns === Vector("RM", "CRIME_low", "CRIME_very_low"))
  assert(encoded.rows.map(_.text("RM")) === Vector("6.0", "6.0", "6.0"))
  assert(encoded.rows.map(_.text("CRIME_low")) === Vector("1", "0", "0"))
  assert(encoded.rows.map(_.text("CRIME_very_low")) === Vector("0", "0", "1"))
}
```

`Table` は不変の case class なので、行ごとに新しい `Row` を作ります。

```scala
/** 列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。値が一致すれば "1"、それ以外は "0"。 */
def encode(table: Table, column: String, categories: Vector[String]): Table =
  val dummyColumns = categories.map(category => s"${column}_$category")
  Table(
    table.columns.filterNot(_ == column) ++ dummyColumns,
    table.rows.map(encodeRow(_, column, categories))
  )

private def encodeRow(row: Row, column: String, categories: Vector[String]): Row =
  val value = row.text(column)
  val dummies =
    categories.map(category => s"${column}_$category" -> (if category == value then "1" else "0"))
  Row(row.cells.removed(column) ++ dummies)
```

Java 版は `new HashMap<>(row.cells())` と写してから `remove`・`put` で書き換えました。Scala の `Map` は不変なので、`removed` と `++` が新しい `Map` を返します。「写してから壊す」ではなく「元を残したまま別の値を作る」書き方になり、元の行が変わらないことをテストで確かめる必要すらありません。

セルは文字列のまま `"1"`・`"0"` にしておきます。こうすると、第 2 章の `Preprocessing.splitFeaturesAndTarget`・`columnMeans`・`fillMissing` を、ダミー変数の列にもそのまま使えます。カテゴリを引数で受け取るのは Kotlin 版・Java 版と同じ理由で、訓練データで決めたカテゴリをテストデータにも当てはめ、列をそろえるためです。

## 9.5 特徴量を標準化する

### 標準化とは

列ごとに平均を引いて標準偏差で割り、平均 0・標準偏差 1 にそろえることを **標準化** と呼びます。単位や桁の違う列（部屋数と税率など）を同じ尺度で比べられるようになります。平均と標準偏差は訓練データだけで求め、テストデータにも同じ値を使います。テストデータの平均を使うと、テストデータの情報が学習に漏れるからです。

### 平均と標準偏差を求める

第 14 章（K-means）でもこのクラスを使うので、第 2 章の `Features` の `Vector` をそのまま受け取れる形にします。

```scala
test("訓練データから列ごとの平均と標準偏差を求める") {
  val standardizer = Standardizer.fit(Vector(row(1, 10), row(2, 10), row(3, 40)))

  assert(standardizer.means("RM") === 2.0 +- 1e-12)
  assert(standardizer.means("LSTAT") === 20.0 +- 1e-12)
  assert(standardizer.stds("RM") === math.sqrt(2.0 / 3) +- 1e-12)
  assert(standardizer.stds("LSTAT") === math.sqrt(200.0) +- 1e-12)
}

test("平均と標準偏差は列の順を保つ") {
  val standardizer = Standardizer.fit(Vector(row(1, 10), row(2, 40)))

  assert(standardizer.means.keys.toVector === Columns)
}
```

RM の標準偏差を `√(2/3)` としているのは、件数（3）で割る標準偏差だからです。scikit-learn の `StandardScaler` と同じ定義にしました。

テストを実行すると、まだ `Standardizer` が無いのでコンパイルで失敗します。

```text
[error] -- [E006] Not Found Error: .../chapter09/StandardizerSpec.scala:14:23
[error] 14 |    val standardizer = Standardizer.fit(Vector(row(1, 10), row(2, 10), row(3, 40)))
[error]    |                       ^^^^^^^^^^^^
[error]    |                    Not found: Standardizer - did you mean standardizer?
```

Scala 3 のコンパイラは、綴りの近い名前（ここでは同じ行で定義している `standardizer`）を候補として出してくれます。実装は次のとおりです。

```scala
case class Standardizer(means: SeqMap[String, Double], stds: SeqMap[String, Double]):
  require(means.keySet == stds.keySet, "平均と標準偏差の列が違います")

  /** 1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。 */
  def transform(features: Features): Features =
    Features(
      features.columns,
      features.columns.zip(features.values).map { (column, value) =>
        means.get(column).fold(value)(mean => (value - mean) / stds(column))
      }
    )

  /** 特徴量のリストを標準化する。 */
  def transform(x: Vector[Features]): Vector[Features] = x.map(transform)

object Standardizer:

  /** 特徴量のすべての列について、平均と標準偏差を求める。 */
  def fit(x: Vector[Features]): Standardizer =
    require(x.nonEmpty, "特徴量が 1 件もありません")
    val stats = x.head.columns.map { column =>
      val values = x.map(_.value(column))
      val mean = values.sum / values.size
      val std = math.sqrt(values.map(value => (value - mean) * (value - mean)).sum / values.size)
      (column, mean, if std == 0 then 1.0 else std)
    }
    Standardizer(
      SeqMap.from(stats.map((column, mean, _) => column -> mean)),
      SeqMap.from(stats.map((column, _, std) => column -> std))
    )
```

Java 版・Kotlin 版との違いを 4 つ挙げます。

1. **順序を保つ `Map`** — Java 版は「順序を保ちたいので `LinkedHashMap` に写してから `Collections.unmodifiableMap` で包む」と 2 段構えでした。Scala には不変で挿入順を保つ `scala.collection.immutable.SeqMap` があるので、型でそのまま表せます。`SeqMap[String, Double]` と書けば「順序のある、変更できない対応表」であることが呼び出し側にも伝わります
2. **列ごとの統計を 1 回で作る** — 平均と標準偏差を別々のループで作らず、`(列名, 平均, 標準偏差)` のタプルの `Vector` を 1 回作ってから 2 つの `SeqMap` に分けます。可変の `Map` に `put` していく Java 版と違い、途中の状態がありません
3. **`Option` で「その列を標準化するか」を表す** — `means.get(column)` は `Option[Double]` を返すので、`fold(value)(...)` で「無ければ元の値のまま」と書けます。Java 版の `if (means.containsKey(column))` と `means.get(column)` の 2 回引きが 1 回になります
4. **配列を壊さない** — Java 版は `Features.values()` が配列の写しを返すことに頼って、その場で書き換えていました。Scala の `Features` は `Vector[Double]` を持つ不変の case class なので、`map` した結果で新しい `Features` を作るだけです。「標準化する列に無い列はそのまま残す」テストも `assert(standardizer.transform(row(3, 7)) === row(2, 7))` と、case class の等価判定でそのまま書けます

`require` は、満たされなければ `IllegalArgumentException` を投げます。Java 版が `if (...) throw new IllegalArgumentException(...)` と書いていた表明を 1 行で書けます。

### Tribuo の標準化と突き合わせる

Tribuo には `MeanStdDevTransformation` という標準化があります。Java 向けの API ですが、Scala からもそのまま呼べます。

```scala
/** 訓練データの値から平均と標準偏差を求め、別の値を標準化する。 */
def standardize(train: Vector[Double], values: Vector[Double]): Vector[Double] =
  val statistics = MeanStdDevTransformation().createStats()
  train.foreach(statistics.observeValue)
  val transformer = statistics.generateTransformer()
  values.map(transformer.transform)
```

`statistics.observeValue` と `transformer.transform` は Java のメソッドですが、Scala 側で関数が求められる場所に書けば自動的に関数値になります（イータ拡張）。Java 版の `statistics::observeValue` と同じことを、記号なしで書けます。

まず、自作の標準化と同じ値になるはずだと考えて、そのまま比べるテストを書いてみました。最初に書いたテストは、未使用の `val` があるというコンパイルエラーになりました。

```text
[error] -- [E198] Unused Symbol Error: .../chapter09/TribuoStandardizationSpec.scala:8:14
[error] 8 |  private val Test = Vector(6.2, 8.0)
[error]   |              ^^^^
[error]   |              unused private member
[error] one error found
```

第 5 章で `-Wunused:all -Xfatal-warnings` を設定したので、テストコードでも使っていない値はエラーになります。使う値だけを残して書き直すと、今度はテストが落ちました。

```text
[info] - Tribuo の標準化は自作の標準化と同じ値になる *** FAILED ***
[info]   2.197401062294143 did not equal 1.9030051422496395 +- 1.0E-12 (TribuoStandardizationSpec.scala:12)
```

2 つの値の比は 0.866 で、`√(3/4)` です。訓練データは 4 件なので、Tribuo は件数から 1 を引いた 3 で割る **不偏標準偏差** を使っていることが分かります。Kotlin 版（ADR 002）・Java 版（ADR 005）で確かめた結果と、値まで同じです。この事実をテストに残します。

```scala
test("Tribuo の MeanStdDevTransformation は件数から 1 を引いて割る標準偏差を使う") {
  val mean = Train.sum / Train.size
  val sumOfSquares = Train.map(value => (value - mean) * (value - mean)).sum
  val sampleStd = math.sqrt(sumOfSquares / (Train.size - 1))

  val standardized = TribuoStandardization.standardize(Train, Values)

  assert(standardized(0) === (6.2 - mean) / sampleStd +- 1e-12)
  assert(standardized(1) === (8.0 - mean) / sampleStd +- 1e-12)
}

test("自作の標準化に件数から決まる係数を掛けると Tribuo の値になる") {
  val standardizer = Standardizer.fit(Train.map(Samples.rm))
  val ratio = math.sqrt((Train.size - 1.0) / Train.size)

  val tribuo = TribuoStandardization.standardize(Train, Values)

  Values.zip(tribuo).foreach { (value, expected) =>
    assert(standardizer.transform(Samples.rm(value)).value("RM") * ratio === expected +- 1e-12)
  }
}
```

同じ「標準偏差」でも、ライブラリによって割る数が違います。件数が多ければ差は小さくなりますが、この章の訓練データは 70 件なので、係数は `√(69/70)` ≒ 0.993 です。線形回帰では係数の大きさが変わるだけで予測は変わりませんが、第 14 章の K-means のように距離を使うアルゴリズムでは、どちらの定義を使ったかを記事とコードで明示しておきます。F# 版は ML.NET を相手にして、既定では平均を引かない（`fixZero = true`）という別の癖に出会いました。「ライブラリの正規化は、名前が同じでも定義が同じとは限らない」という教訓は、どの言語でも同じです。

## 9.6 多項式特徴量を作る

### 2 乗の項と交互作用の項

部屋数（RM）と価格の関係が直線でなく曲線なら、RM の 2 乗の列を加えると線形回帰でも曲線を表せます。2 つの列の積（交互作用の項）を加えると、「部屋数が多く、かつ低所得者の割合が低い」のような組み合わせの効果を表せます。

```scala
test("2 列なら 2 乗の列と 2 つの列の積の列を加える") {
  val columns = Vector("RM", "LSTAT")
  val x = Vector(Features(columns, Vector(2, 5)), Features(columns, Vector(3, 7)))

  val features = PolynomialFeatures.expand(x, columns)

  assert(features.head.columns === Vector("RM", "LSTAT", "RM^2", "RM LSTAT", "LSTAT^2"))
  assert(features.map(_.value("RM LSTAT")) === Vector(10.0, 21.0))
  assert(features.map(_.value("LSTAT^2")) === Vector(25.0, 49.0))
}
```

1 列・2 列・3 列と三角測量し、3 列では scikit-learn の `PolynomialFeatures` と同じ並びの 9 列になることを確かめました。項は、左右 2 つの列名を持つ case class で表します。

```scala
/** 2 次の項。left と right が同じなら 2 乗の項を表す。 */
case class Term(left: String, right: String):

  /** 項の名前。scikit-learn の get_feature_names_out と同じ形（"RM^2"、"RM LSTAT"）にする。 */
  def name: String = if left == right then s"$left^2" else s"$left $right"

/** 重複を許して 2 つの列を選ぶ組を、scikit-learn の PolynomialFeatures と同じ順に並べる。 */
def pairsWithReplacement(columns: Vector[String]): Vector[Term] =
  columns.indices.toVector.flatMap(i => columns.drop(i).map(right => Term(columns(i), right)))
```

Kotlin 版は `Pair<String, String>` と別の関数 `termName` を使い、Java 版は `Pair` という record にメソッドを持たせました。Scala 版は `Term` という名前の case class にします。`Pair` より、この章で何を表す組なのかがはっきりします。`name` に引数が無いので、`term.name` と値のように読めるのも Scala らしいところです。

二重ループは `indices.flatMap` と `drop(i)` で書きました。Java 版の `for (int i...) for (int j = i...)` と同じ順に並びます。

```scala
/** 指定した列の後ろに、2 乗の項と交互作用の項を加える。 */
def expand(x: Vector[Features], columns: Vector[String]): Vector[Features] =
  val terms = pairsWithReplacement(columns)
  val names = columns ++ terms.map(_.name)
  x.map(features =>
    Features(
      names,
      columns.map(features.value) ++ terms.map(term =>
        features.value(term.left) * features.value(term.right)
      )
    )
  )
```

Java 版は結果の `double[]` を確保して添字で埋めましたが、Scala では「元の列の値」と「項の値」を作って `++` でつなぐだけです。添字の計算（`values[columns.size() + k]`）が消えるので、ずれる余地がありません。

使う項を選ぶ `select` も用意し、「元の特徴量だけ」「2 乗の項を追加」「交互作用の項も追加」を同じ流れで比べられるようにしました。

## 9.7 外れ値を検出する

第 1 四分位数（Q1）と第 3 四分位数（Q3）の差を **四分位範囲（IQR）** と呼びます。`Q1 − 1.5 × IQR` より小さい値と、`Q3 + 1.5 × IQR` より大きい値を外れ値とみなします。

```scala
test("四分位数の位置が値の間にあれば前後の値から線形補間する") {
  assert(Outliers.quantile(Vector(4.0, 1.0, 3.0, 2.0), 0.25) === 1.75 +- 1e-12)
}

test("第 3 四分位数から IQR の 1.5 倍より大きい値を外れ値とする") {
  assert(
    Outliers.iqrOutliers(Vector(1.0, 2.0, 3.0, 4.0, 100.0)) ===
      Vector(false, false, false, false, true)
  )
}
```

```scala
/** 分位数を求める。位置が値の間にあれば前後の値から線形補間する（pandas の quantile の既定と同じ）。 */
def quantile(values: Vector[Double], q: Double): Double =
  val sorted = values.sorted
  val position = (sorted.size - 1) * q
  val lower = math.floor(position).toInt
  val upper = math.ceil(position).toInt
  sorted(lower) + (sorted(upper) - sorted(lower)) * (position - lower)

/** 第 1 四分位数から IQR の k 倍より小さい値と、第 3 四分位数から k 倍より大きい値を外れ値とする。 */
def iqrOutliers(values: Vector[Double], k: Double = DefaultK): Vector[Boolean] =
  val q1 = quantile(values, FirstQuartile)
  val q3 = quantile(values, ThirdQuartile)
  val iqr = q3 - q1
  values.map(value => value < q1 - k * iqr || value > q3 + k * iqr)
```

Java 版は既定の引数が無いので `iqrOutliers(values)` と `iqrOutliers(values, k)` の 2 つをオーバーロードしました。Scala は Kotlin 版と同じく `k: Double = DefaultK` と既定値を書けるので、メソッドは 1 つで済みます。

外れ値を除く `removeTargetOutliers` は、訓練データから正解（価格）が外れ値の行だけを取り除き、テストデータには手を付けません。テストデータは「本番で来るデータ」の代わりなので、外れ値を含んでいても評価から外してはいけないからです。

```scala
def removeTargetOutliers(
    split: TrainTestSplit[Features, Double]
): TrainTestSplit[Features, Double] =
  val kept = split.xTrain.zip(split.tTrain).zip(iqrOutliers(split.tTrain)).collect {
    case (pair, false) => pair
  }
  TrainTestSplit(kept.map(_._1), split.xTest, kept.map(_._2), split.tTest)
```

Java 版は「残す添字の `List<Integer>` を作り、特徴量と正解をそれぞれ添字で引く」と書きました。Scala では特徴量・正解・外れ値かどうかの 3 つを `zip` でそろえ、`collect` に `case (pair, false)` というパターンを渡して「外れ値でない組」だけを残せます。添字が出てこないので、特徴量と正解がずれる心配がありません。

## 9.8 表を結合して特徴量を増やす

### タブ区切りと Shift_JIS

区切り文字と文字コードを受け取って `Table` を作る `DelimitedFiles.load` を足します。

```scala
/** 1 行目を列名として読み込む。文字コードが違えば MalformedInputException を投げる。 */
def load(file: Path, charset: Charset, delimiter: String): Table =
  val separator = Pattern.quote(delimiter)
  val lines = Files.readAllLines(file, charset).asScala.toVector
  val columns = lines.head.split(separator, -1).toVector
  val rows = lines.tail.filter(_.trim.nonEmpty).map(toRow(columns, _, separator))
  Table(columns, rows)
```

`split` は正規表現を受け取るので、区切り文字を `Pattern.quote` で囲んでおきます（`|` などを区切り文字にしても誤動作しないように）。`bike.tsv` は `"\t"` と UTF-8、`weather.csv` は `","` と `Charset.forName("Shift_JIS")` で読み込みます。Scala の標準ライブラリには CSV の読み込みが無いので、第 2 章と同じく Java の `Files.readAllLines` を `asScala.toVector` で不変のコレクションに変えて使います。

文字コードを間違えたときの振る舞いは、Java 版と同じでした。

```scala
test("Shift_JIS のファイルを UTF-8 として読むと例外になる") {
  val csvFile = writeFile("weather.csv", "weather_id,weather\n1,晴れ\n", BikeWeather.ShiftJis)

  assertThrows[MalformedInputException] {
    DelimitedFiles.load(csvFile, StandardCharsets.UTF_8, ",")
  }
}
```

`Files.readAllLines` は、復号できないバイトがあると置き換えずに `MalformedInputException` で知らせます。Kotlin 版で使った Kotlin DataFrame は、UTF-8 として解釈できないバイトを置換文字（U+FFFD）に置き換えて読み込みを続けました。文字化けしたデータが黙って入ってこない点では、JVM の標準の読み込み方のほうが安全です。JVM の言語（Java・Kotlin・Scala）でも、どのライブラリで読むかで振る舞いが変わることが分かります。

### Map による結合と集計

利用者数の表に、天気 ID をキーにして天気の名前を加えます。Kotlin 版は `innerJoin` を使いました。Scala 版は Java 版と同じく、天気の表を「天気 ID → 行」の `Map` にしてから、利用者数の行ごとに引きます。

```scala
/** 天気 ID をキーにした Map を引いて、天気の列を加える（内部結合）。天気の表に無い ID の行は残さない。 */
def joinWeather(bike: Table, weather: Table): Table =
  val byId = weather.rows.map(row => row.text(Key) -> row).toMap
  require(byId.size == weather.rows.size, s"$Key が一意ではありません")
  val added = weather.columns.filterNot(_ == Key)
  val rows = bike.rows.flatMap(row => byId.get(row.text(Key)).map(join(row, _, added)))
  Table(bike.columns ++ added, rows)
```

`flatMap` と `Option` の組み合わせが、この章でいちばん Scala らしい行です。`byId.get(...)` は `Option[Row]` を返すので、`map` で結合し、`flatMap` で `None`（天気の表に無い天気 ID）を落とします。「探す・あれば使う・無ければ捨てる」が 1 行になり、Java 版の `filter(containsKey)` と `map(get)` の 2 段が要りません。

ただし、Scala の `toMap` はキーが重複すると後の値で静かに上書きします。Java 版の `Collectors.toMap` は例外を投げるので、そこだけ振る舞いが違います。「天気 ID が一意でなければ結合の前に気付ける」という Java 版の安全性を保つために、`require` で件数を確かめました。**便利な既定が、必ずしも安全な既定とは限りません。**

天気ごとの平均利用者数は、`groupBy` と `sortBy` で求めます。

```scala
/** 天気ごとの平均利用者数を、多い順に並べて返す。 */
def meanCountByWeather(joined: Table): Vector[(String, Double)] =
  joined.rows
    .groupBy(_.text("weather"))
    .view
    .mapValues(rows => rows.flatMap(_.number("cnt")).sum / rows.size)
    .toVector
    .sortBy(-_._2)
```

Java 版は「`groupingBy` が返す `HashMap` は順序を持たないので、並べ替えてから `LinkedHashMap::new` に集める」と書きました。Scala 版は、順序が要る結果を `Map` ではなく `Vector[(String, Double)]` で返します。**順序を持たない型に順序を期待しない** ほうが、型と意図が一致します。`.view` を挟むと `mapValues` が遅延評価になり、中間の `Map` を作らずに済みます。

## 9.9 特徴量の効果を測る

### Boston データの前処理

`Boston.prepare` は、CRIME をダミー変数にしてから、第 2 章の関数で「特徴量と正解に分ける → シード付きで分割する → 訓練データの平均値で両方の欠損値を補完する」を行います。

```scala
def prepare(csvFile: Path, testSize: Double, seed: Long): TrainTestSplit[Features, Double] =
  val table = Table.load(csvFile)
  val encoded =
    Dummies.encode(table, Category, Dummies.categories(table.rows.map(_.text(Category))))
  val (columns, rows, target) = Preprocessing.splitFeaturesAndTarget(encoded, Target)
  val split = Preprocessing.splitTrainTest(rows, target.map(_.toDouble), testSize, seed)
  val means = Preprocessing.columnMeans(split.xTrain, columns)
  TrainTestSplit(
    Preprocessing.fillMissing(split.xTrain, columns, means),
    Preprocessing.fillMissing(split.xTest, columns, means),
    split.tTrain,
    split.tTest
  )
```

第 2 章の `splitFeaturesAndTarget` は `(列名, 行, 正解)` のタプルを返すので、`val (columns, rows, target) = ...` と一度に受け取れます。Java 版は同じことをするために `FeaturesAndTarget` という record を用意しました。

Kotlin 版では、欠損のある整数の列（RAD）が `Int?` として読み込まれ、平均値の `Double` を入れられないという型の問題が実データで見つかりました。Scala 版の `Row` はセルを文字列で持ち、`number` で読むときに `Option[Double]` にするので、この問題は起きません。列の型を読み込み時に推論しない代わりに、読むたびに解釈する設計の違いです。

### 線形回帰と決定係数

決定係数を測るために、この章に正規方程式を解く最小の `LinearModel` を置きました。Tribuo の `DenseMatrix` のコレスキー分解で `XᵀX β = Xᵀt` を解きます。

```scala
/** 先頭に 1 の列を加えた計画行列 X で、正規方程式 XᵀX β = Xᵀt を解く。 */
def fit(rows: Vector[Vector[Double]], t: Vector[Double]): LinearModel =
  val design = rows.map(row => (1.0 +: row).toArray).toArray
  val x = DenseMatrix.createDenseMatrix(design)
  val transposed = x.transpose()
  val cholesky = transposed
    .matrixMultiply(x)
    .choleskyFactorization()
    .toScala
    .getOrElse(throw IllegalArgumentException("特徴量の列が互いに独立でないため、正規方程式を解けません"))
  val target = DenseVector.createDenseVector(t.toArray)
  val beta = cholesky.solve(transposed.leftMultiply(target)).toArray.toVector
  LinearModel(beta.head, beta.tail)
```

Tribuo は Java のライブラリなので、行列は `Array[Array[Double]]`、`choleskyFactorization()` の戻り値は `java.util.Optional` です。Scala の不変コレクションとの境目は次の 3 か所だけで済みました。

| 境目 | 橋渡し |
|------|-------|
| `Vector[Vector[Double]]` → `Array[Array[Double]]` | `rows.map(row => (1.0 +: row).toArray).toArray` |
| `java.util.Optional[T]` → `Option[T]` | `scala.jdk.OptionConverters` の `.toScala` |
| `Array[Double]` → `Vector[Double]` | `.toVector` |

`Optional` を `Option` に変えてしまえば、`getOrElse` で「解けない」ことを例外にできます（Java 版の `orElseThrow`、Kotlin 版の `orElseThrow { ... }` に当たります）。`1.0 +: row` で先頭に 1 を足せるので、Java 版の `System.arraycopy` は要りません。

特徴量の組ごとの評価は `Boston.scoreFeatureSet` にまとめました。訓練データとテストデータのそれぞれで多項式特徴量を作って項を選び、**訓練データで** `Standardizer` を `fit` し、両方を `transform` してから学習します。

```scala
def scoreFeatureSet(
    split: TrainTestSplit[Features, Double],
    columns: Vector[String],
    terms: Vector[String]
): Scores =
  val train = PolynomialFeatures.select(PolynomialFeatures.expand(split.xTrain, columns), terms)
  val test = PolynomialFeatures.select(PolynomialFeatures.expand(split.xTest, columns), terms)
  val standardizer = Standardizer.fit(train)
  val xTrain = standardizer.transform(train).map(_.values)
  val xTest = standardizer.transform(test).map(_.values)
  val model = LinearModel.fit(xTrain, split.tTrain)
  Scores(
    LinearModel.rSquared(split.tTrain, model.predict(xTrain)),
    LinearModel.rSquared(split.tTest, model.predict(xTest))
  )
```

Kotlin 版は訓練とテストの決定係数を `Pair<Double, Double>` で返しましたが、Scala 版は Java 版と同じく `Scores(train: Double, test: Double)` という case class にしました。タプルの `_1`・`_2` ではなく `scores.train`・`scores.test` と読めます。**タプルは作るのが楽ですが、読む人には名前が要ります。**

価格を `3 × RM² + 1` にした架空のデータで、「2 乗の項が無いと当てきれない」「2 乗の項を加えると訓練データもテストデータも決定係数が 1 になる」ことを確かめてから、実データに進みました。

### 実データで測る

`sbt "run chapter09"` の出力です。

```text
訓練データ: 70 件, テストデータ: 30 件
特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
決定係数:
  元の特徴量（3 列）: 訓練 0.6056, テスト 0.6950
  2 乗の項を追加（6 列）: 訓練 0.7740, テスト 0.8628
  交互作用の項も追加（9 列）: 訓練 0.7953, テスト 0.8213
訓練データの PRICE の外れ値: 8 件
  外れ値を除いて 2 乗の項を追加: 訓練 0.6717, テスト 0.7947
天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
```

「標準化した訓練データの RM」は、標準化した訓練データにもう一度 `Standardizer.fit` を当て、その平均と標準偏差を表示しています。

- **2 乗の項** を加えると、テストデータの決定係数が 0.6950 から 0.8628 に上がりました
- **交互作用の項** も加えると、訓練データでは上がる（0.7740 → 0.7953）のに、テストデータでは下がりました（0.8628 → 0.8213）。列を増やすと訓練データには合わせやすくなりますが、未知のデータへの当てはまりが良くなるとは限りません
- **外れ値** を除いて学習すると、テストデータの決定係数は 0.7947 に下がりました。テストデータには外れ値が残っているので、外れ値を学ばなかったモデルはそれを当てられません
- 天気ごとの平均利用者数は、ほかの版と同じ値になりました（分割に関係しないため）

**決定係数の 8 つの数値は、Java 版の記事とすべて一致しました。** 第 2 章で書いたとおり、Scala 版の `Preprocessing.splitTrainTest` は `java.util.Random` と Fisher-Yates のシャッフルで Java 版（`Collections.shuffle`）と同じ並びを作ります。同じ行が訓練データとテストデータに入るので、そのあとの標準化・多項式特徴量・正規方程式まで含めて同じ値になります。Kotlin 版は `kotlin.random.Random(0)` を使うので分け方が違い、2 乗の項でテスト 0.6457 → 0.7975 と別の値になりました。F# 版はさらに別の乱数生成器です。

**「実装が正しい」ことの確かめ方が 1 つ増えた** とも言えます。言語をまたいでも、乱数の作り方と手順をそろえれば数値は完全に一致します。逆に言えば、数値が合わないときは乱数か手順のどちらかが違う、と切り分けられます。「2 乗の項で上がる」「交互作用の項ではテストデータが下がる」という結論は、Java 版・Python 版と同じです。

この出力は `FeatureEngineeringDataSpec` で固定しています。学習データが無い環境では、`assume` で実データのテストをスキップします。

```scala
private def requireData(): org.scalatest.Assertion =
  assume(
    Vector(bostonCsv, bikeTsv, weatherCsv).forall(Files.exists(_: Path)),
    "学習データ Boston.csv・bike.tsv・weather.csv が配置されていない（gulp data:setup）"
  )
```

`assume` は `Assertion` を返すので、`-Wvalue-discard` の設定では戻り値を捨てるとエラーになります。第 2 章と同じく、呼ぶ側で `requireData(): Unit` と型を書いて捨てることを明示します。

## 9.10 Notebook による探索と可視化

Scala 版では Notebook と可視化の節を設けません。標準化の前後の分布、特徴量と価格の関係、外れ値、天気ごとの利用者数のグラフは、[Python 版の第 9 章](../python/09-feature-engineering.md) と [Kotlin 版の第 9 章](../kotlin/09-feature-engineering.md) の「Notebook による探索と可視化」の節を参照してください。

## 9.11 リファクタリング

TODO リストをすべて終えてから、`sbt "scalafmtAll; scalafmtCheckAll; test"` で整形と検査をかけました。

- **警告はすべてエラー** — `-Wunused:all` がテストの未使用の `val` を、`-Wvalue-discard` が `assume` の戻り値を捨てているところを止めました。どちらも抑制せずに直しています
- **`Map` を返すか、`Vector` を返すか** — 並び順に意味がある `meanCountByWeather` は `Vector[(String, Double)]` を返し、列名で引く `Standardizer` の平均・標準偏差は `SeqMap` を返します。「順序が要るか」「キーで引くか」で型を選び分けました
- **`Term` に名前を付けた** — 組を `(String, String)` のタプルのままにせず、`name` を持つ case class にしました

### 第 14 章から使う API

第 14 章（K-means）でも標準化を使うので、`Standardizer` は次の形で公開しています。

| API | 内容 |
|-----|------|
| `Standardizer.fit(x: Vector[Features]): Standardizer` | すべての列の平均と標準偏差（件数で割る）を求める |
| `standardizer.transform(x: Vector[Features]): Vector[Features]` | リストを標準化する |
| `standardizer.transform(features: Features): Features` | 1 件を標準化する。平均を持たない列はそのまま残す |
| `standardizer.means: SeqMap[String, Double]`・`stds` | 列名の順を保った、不変の対応表 |
| `Standardizer(means, stds)` | 平均と標準偏差を直接与えて作る（列が食い違えば `IllegalArgumentException`） |

K-means は距離を使うので、標準偏差の定義（件数で割る）を変えると結果が変わります。Tribuo の `MeanStdDevTransformation`（件数から 1 を引く）ではなく、この `Standardizer` を使うことを第 14 章でも明記します。

## 9.12 まとめ

この章では、特徴量エンジニアリングの 5 つの技法を Scala の TDD で自作し、標準化を Tribuo と突き合わせました。

| 技法 | 自作したもの | 突き合わせたライブラリ | 落とし穴 |
|------|------------|-------------------|---------|
| ダミー変数 | `Dummies.categories`・`encode` | — | 訓練データとテストデータで列をそろえる |
| 標準化 | `Standardizer` | Tribuo の `MeanStdDevTransformation` | 標準偏差の定義がライブラリで違う、分散 0 の列、テストデータの平均を使わない |
| 多項式特徴量 | `PolynomialFeatures.expand`・`Term` | — | 列数が急に増えて過学習しやすい |
| 外れ値の検出 | `Outliers.quantile`・`iqrOutliers` | — | 検出はできても、除くかどうかはデータの意味で決める |
| 表の結合 | `DelimitedFiles.load`・`BikeWeather.joinWeather` | — | 区切り文字と文字コード、内部結合で消える行、`toMap` の静かな上書き |

実データでは、2 乗の項を加えるとテストデータの決定係数が 0.6950 から 0.8628 に上がり、交互作用の項を加えると 0.8213 に、外れ値を除くと 0.7947 に下がりました。これらの値は Java 版と完全に一致しました。

Scala 版ならではの学びもありました。

1. **順序のある不変の `Map` が型で書ける** — Java 版が `LinkedHashMap` + `unmodifiableMap` の 2 段で表したものを、`SeqMap` の 1 語で表せる。順序が要らない結果は `Map`、順序に意味がある結果は `Vector[(K, V)]` と、型で意図を表す
2. **`Option` が結合と既定値をまとめる** — 内部結合は `flatMap` + `Option`、「標準化しない列」は `means.get(column).fold(value)(...)` で、条件分岐を書かずに済む
3. **添字が消える** — `zip`・`collect`・`++` で、特徴量と正解、元の列と項を対応づけられる。Java 版の添字の計算が無くなった分、ずれる余地も無い
4. **Java の API との境目は小さい** — Tribuo の行列と `Optional` は、`toArray`・`toScala`・`toVector` の 3 つだけで橋渡しできた。メソッド参照もイータ拡張でそのまま書ける
5. **乱数と手順をそろえれば数値は一致する** — Java 版と同じ `java.util.Random` の Fisher-Yates を使ったので、決定係数まで完全に一致した

次の章では、分類のモデルを増やし、ロジスティック回帰と、第 3 章の決定木を組み合わせたランダムフォレストを実装します。
