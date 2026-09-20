package machinelearning.chapter13

import java.nio.file.Path
import machinelearning.chapter02.{Features, Preprocessing, Table}
import machinelearning.chapter07.Matrix
import machinelearning.chapter09.{Dummies, Standardizer}

/** 主成分分析のために、ボストンの住宅価格（Boston.csv）のすべての列を前処理する。 */
object BostonPca:

  /** カテゴリ値の列 */
  val Category = "CRIME"

  /** CRIME をダミー変数にし、欠損値を列の平均値で補完してから、すべての列を標準化する。 */
  def standardize(table: Table): Vector[Features] =
    val encoded =
      Dummies.encode(table, Category, Dummies.categories(table.rows.map(_.text(Category))))
    val filled = Preprocessing.fillMissing(
      encoded.rows,
      encoded.columns,
      Preprocessing.columnMeans(encoded.rows, encoded.columns)
    )
    Standardizer.fit(filled).transform(filled)

  /** CSV を読み込んで前処理する。 */
  def load(csvFile: Path): Vector[Features] = standardize(Table.load(csvFile))

  /** 特徴量のリストを、1 件を 1 行とする行列にする。 */
  def toMatrix(x: Vector[Features]): Matrix = Matrix(x.map(_.values))
