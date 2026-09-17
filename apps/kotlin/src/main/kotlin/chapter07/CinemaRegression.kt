package chapter07

import chapter02.TrainTestSplit
import chapter02.columnMeans
import chapter02.fillMissing
import chapter02.splitTrainTest
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.AnyRow
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.convertToDouble
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.filter
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.File

val FEATURES = listOf("SNS1", "SNS2", "actor", "original")
const val TARGET = "sales"

/** SNS2 がこの値を超え、かつ興行収入が OUTLIER_SALES 未満の映画を外れ値とする */
private const val OUTLIER_SNS2 = 1000
private const val OUTLIER_SALES = 8500

data class LinearModel(
    val intercept: Double,
    val coefficients: Map<String, Double>,
) {
    fun predict(x: AnyFrame): List<Double> {
        val features = x.toMatrix(coefficients.keys.toList())
        val weights = Matrix(coefficients.values.map { listOf(it) })
        return (features * weights).columns.first().map { intercept + it }
    }
}

fun AnyFrame.toMatrix(columns: List<String>): Matrix = Matrix(rows().map { row -> columns.map { (row[it] as Number).toDouble() } })

fun loadCinema(csvFile: File): AnyFrame = DataFrame.readCSV(csvFile)

private fun AnyRow.isOutlier(): Boolean =
    (this["SNS2"] as Number).toDouble() > OUTLIER_SNS2 && (this[TARGET] as Number).toDouble() < OUTLIER_SALES

fun removeOutliers(df: AnyFrame): AnyFrame = df.filter { !it.isOutlier() }

fun prepareCinema(
    csvFile: File,
    testSize: Double,
    seed: Int,
): TrainTestSplit<Double> {
    val df = removeOutliers(loadCinema(csvFile))
    val x = dataFrameOf(FEATURES.map { df[it].convertToDouble() })
    val t = df[TARGET].values().map { (it as Number).toDouble() }
    val split = splitTrainTest(x, t, testSize, seed)
    val means = columnMeans(split.xTrain, FEATURES)
    return split.copy(
        xTrain = fillMissing(split.xTrain, means),
        xTest = fillMissing(split.xTest, means),
    )
}

fun fitLinearRegression(
    x: AnyFrame,
    t: List<Double>,
): LinearModel {
    val features = x.columnNames()
    val design = Matrix(x.toMatrix(features).rows.map { listOf(1.0) + it })
    val target = Matrix(t.map { listOf(it) })
    val weights = (design.transpose() * design).solve(design.transpose() * target).columns.first()
    return LinearModel(intercept = weights.first(), coefficients = features.zip(weights.drop(1)).toMap())
}
