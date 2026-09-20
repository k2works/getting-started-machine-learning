package machinelearning.chapter09

import machinelearning.chapter02.{Row, Table}
import machinelearning.chapter02.Features

/** テストで使う架空の特徴量と表。 */
object Samples:

  /** RM の 1 列だけの特徴量。 */
  def rm(value: Double): Features = Features(Vector("RM"), Vector(value))

  /** 列名と値の組から 1 行を作る。 */
  def row(cells: (String, String)*): Row = Row(cells.toMap)

  /** 列名の並びと行から表を作る。 */
  def table(columns: Vector[String], rows: Row*): Table = Table(columns, rows.toVector)
