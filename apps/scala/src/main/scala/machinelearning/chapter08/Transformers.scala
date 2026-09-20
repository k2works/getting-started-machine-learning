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
