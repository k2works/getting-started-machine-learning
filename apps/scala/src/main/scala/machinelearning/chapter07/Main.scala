package machinelearning.chapter07

import java.nio.file.Paths
import java.util.Locale
import machinelearning.chapter02.Table
import machinelearning.dataset.DataDir

/** 映画の興行収入を線形回帰で予測し、係数と評価指標を表示する。 */
object Main:
  private val TestSize = 0.2
  private val Seed = 0L

  def run(print: String => Unit): Unit =
    val csvFile = Paths.get(DataDir.current(), "cinema.csv")
    val table = Table.load(csvFile)
    val split = Cinema.prepare(csvFile, TestSize, Seed)
    val model = LinearRegression.fit(split.xTrain, split.tTrain)
    val y = model.predict(split.xTest)
    val coefficients =
      model.coefficients.map((name, value) => s"$name=${format(value, 4)}").mkString(", ")
    print(s"データ件数: ${table.rows.size}")
    print(s"外れ値を除いた件数: ${Cinema.removeOutliers(table).rows.size}")
    print(s"訓練データ: ${split.xTrain.size} 件, テストデータ: ${split.xTest.size} 件")
    print(s"切片: ${format(model.intercept, 2)}")
    print(s"係数: $coefficients")
    print(
      "テストデータの評価: " +
        s"R2=${format(RegressionMetrics.r2Score(split.tTest, y), 4)}, " +
        s"MAE=${format(RegressionMetrics.meanAbsoluteError(split.tTest, y), 2)}, " +
        s"RMSE=${format(RegressionMetrics.rootMeanSquaredError(split.tTest, y), 2)}"
    )

  private def format(value: Double, decimals: Int): String =
    String.format(Locale.ROOT, s"%.${decimals}f", value)
