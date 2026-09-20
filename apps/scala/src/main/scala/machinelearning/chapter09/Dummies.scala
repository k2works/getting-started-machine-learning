package machinelearning.chapter09

import machinelearning.chapter02.{Row, Table}

/** カテゴリ値の列を、カテゴリごとの 0 と 1 の列（ダミー変数）に変える。 */
object Dummies:

  /** 欠損値（空欄）を除いたカテゴリを辞書順に並べ、先頭を除いて返す（pandas の drop_first=True と同じ）。 */
  def categories(values: Vector[String]): Vector[String] =
    values.filter(_.trim.nonEmpty).distinct.sorted.drop(1)

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
