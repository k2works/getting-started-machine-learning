package machinelearning.chapter09

import java.nio.charset.Charset
import java.nio.file.{Files, Path}
import java.util.regex.Pattern
import machinelearning.chapter02.{Row, Table}
import scala.jdk.CollectionConverters.*

/** 文字コードと区切り文字を指定して、区切り文字で区切ったファイルを [[machinelearning.chapter02.Table]] に読み込む。 */
object DelimitedFiles:

  /** 1 行目を列名として読み込む。文字コードが違えば MalformedInputException を投げる。 */
  def load(file: Path, charset: Charset, delimiter: String): Table =
    val separator = Pattern.quote(delimiter)
    val lines = Files.readAllLines(file, charset).asScala.toVector
    val columns = lines.head.split(separator, -1).toVector
    val rows = lines.tail.filter(_.trim.nonEmpty).map(toRow(columns, _, separator))
    Table(columns, rows)

  private def toRow(columns: Vector[String], line: String, separator: String): Row =
    Row(columns.zip(line.split(separator, -1).toVector).toMap)
