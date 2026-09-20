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
