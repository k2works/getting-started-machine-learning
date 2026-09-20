package machinelearning.chapter08

import machinelearning.chapter02.{Row, Table}

/** テスト用の表を作り、列の値を読む。 */
object Tables:

  /** 1 つ目の引数を列名の並び、残りを行として、カンマ区切りの文字列から表を作る。空欄は欠損値。 */
  def table(header: String, lines: String*): Table =
    val columns = header.split(",", -1).toVector
    Table(columns, lines.toVector.map(line => Row(columns.zip(line.split(",", -1).toVector).toMap)))

  /** 数値の列の値を、上から順に並べる。欠損値があれば失敗する。 */
  def numbers(table: Table, column: String): Vector[Double] =
    table.rows.map(_.number(column).getOrElse(throw IllegalStateException(s"欠損値です: $column")))

  /** 文字列の列の値を、上から順に並べる。 */
  def texts(table: Table, column: String): Vector[String] = table.rows.map(_.text(column))
