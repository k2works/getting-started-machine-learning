package chapter07

import dataset.dataDir
import java.io.File
import java.util.Locale

private const val TEST_SIZE = 0.2
private const val SEED = 0

private fun fourDecimals(value: Double): String = "%.4f".format(Locale.ROOT, value)

private fun twoDecimals(value: Double): String = "%.2f".format(Locale.ROOT, value)

fun main() {
    val csvFile = File(dataDir(), "cinema.csv")
    val df = loadCinema(csvFile)
    val split = prepareCinema(csvFile, testSize = TEST_SIZE, seed = SEED)
    val model = fitLinearRegression(split.xTrain, split.tTrain)
    val y = model.predict(split.xTest)
    val coefficients = model.coefficients.entries.joinToString(", ") { (name, value) -> "$name=${fourDecimals(value)}" }
    println("データ件数: ${df.rowsCount()}")
    println("外れ値を除いた件数: ${removeOutliers(df).rowsCount()}")
    println("訓練データ: ${split.xTrain.rowsCount()} 件, テストデータ: ${split.xTest.rowsCount()} 件")
    println("切片: ${twoDecimals(model.intercept)}")
    println("係数: $coefficients")
    println(
        "テストデータの評価: R2=${fourDecimals(r2Score(split.tTest, y))}, " +
            "MAE=${twoDecimals(meanAbsoluteError(split.tTest, y))}, " +
            "RMSE=${twoDecimals(rootMeanSquaredError(split.tTest, y))}",
    )
}
