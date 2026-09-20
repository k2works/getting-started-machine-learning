package machinelearning.chapter07

import java.nio.file.Path
import machinelearning.chapter02.{Features, Preprocessing, Row, Table, TrainTestSplit}

/** 映画の興行収入のデータ（cinema.csv）の前処理。 */
object Cinema:

  /** 特徴量の列 */
  val FeatureColumns: Vector[String] = Vector("SNS1", "SNS2", "actor", "original")

  /** 正解ラベルの列 */
  val Target = "sales"

  /** SNS2 がこの値を超え、かつ興行収入が OutlierSales 未満の映画を外れ値とする */
  private val OutlierSns2 = 1000.0

  private val OutlierSales = 8500.0

  /** 外れ値の行を除いた表を返す。 */
  def removeOutliers(table: Table): Table =
    table.copy(rows = table.rows.filterNot(isOutlier))

  /** 外れ値を除き、分割してから訓練データの平均値で両方の欠損値を補完する。 */
  def prepare(csvFile: Path, testSize: Double, seed: Long): TrainTestSplit[Features, Double] =
    val table = removeOutliers(Table.load(csvFile))
    val t = table.rows.map(number(_, Target))
    val split = Preprocessing.splitTrainTest(table.rows, t, testSize, seed)
    val means = Preprocessing.columnMeans(split.xTrain, FeatureColumns)
    TrainTestSplit(
      Preprocessing.fillMissing(split.xTrain, FeatureColumns, means),
      Preprocessing.fillMissing(split.xTest, FeatureColumns, means),
      split.tTrain,
      split.tTest
    )

  private def isOutlier(row: Row): Boolean =
    number(row, "SNS2") > OutlierSns2 && number(row, Target) < OutlierSales

  private def number(row: Row, column: String): Double =
    row.number(column).getOrElse(throw IllegalArgumentException(s"欠損値です: $column"))
