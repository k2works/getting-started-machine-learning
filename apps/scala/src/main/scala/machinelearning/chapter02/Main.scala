package machinelearning.chapter02

import java.nio.file.Paths
import machinelearning.dataset.DataDir

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
