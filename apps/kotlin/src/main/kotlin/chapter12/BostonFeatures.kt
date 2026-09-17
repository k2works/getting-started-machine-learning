package chapter12

import chapter02.splitTrainTest
import chapter07.Matrix
import chapter07.toMatrix
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.convertToDouble
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

data class PolynomialScaler(
    val inputNames: List<String>,
    val means: List<Double>,
    val stds: List<Double>,
) {
    /** 2 次の項を作る列の組（i <= j）。(0, 0) は 1 列目の 2 乗、(0, 1) は 1 列目と 2 列目の積 */
    private val pairs: List<Pair<Int, Int>> = inputNames.indices.flatMap { i -> (i until inputNames.size).map { j -> i to j } }

    val featureNames: List<String> =
        inputNames + pairs.map { (i, j) -> if (i == j) "${inputNames[i]}^2" else "${inputNames[i]} ${inputNames[j]}" }

    fun transform(x: AnyFrame): Matrix =
        Matrix(
            x.toMatrix(inputNames).rows.map { row ->
                val z = row.indices.map { (row[it] - means[it]) / stds[it] }
                z + pairs.map { (i, j) -> z[i] * z[j] }
            },
        )
}

/** 列ごとの z スコア（標準偏差は件数 n - 1 で割る標本標準偏差）の絶対値が threshold を超える値を 1 つでも持つ行を除く */
fun removeOutliers(
    df: AnyFrame,
    columns: List<String>,
    threshold: Double,
): AnyFrame {
    val values = df.toMatrix(columns)
    val means = values.columns.map { it.average() }
    val stds = values.columns.zip(means) { column, mean -> sqrt(column.sumOf { (it - mean) * (it - mean) } / (column.size - 1)) }

    fun isOutlier(row: List<Double>): Boolean = row.indices.any { abs((row[it] - means[it]) / stds[it]) > threshold }

    return df[values.rows.indices.filterNot { isOutlier(values.rows[it]) }]
}

/** 平均値と、件数 n で割る標準偏差（母標準偏差）を訓練データから求める */
fun fitPolynomialScaler(x: AnyFrame): PolynomialScaler {
    val names = x.columnNames()
    val columns = x.toMatrix(names).columns
    val means = columns.map { it.average() }
    val stds = columns.zip(means) { column, mean -> sqrt(column.sumOf { (it - mean) * (it - mean) } / column.size) }
    return PolynomialScaler(inputNames = names, means = means, stds = stds)
}

val FEATURES = listOf("RM", "PTRATIO", "LSTAT")
const val TARGET = "PRICE"
const val OUTLIER_THRESHOLD = 3.0

data class BostonDataset(
    val xTrain: Matrix,
    val tTrain: List<Double>,
    val xValid: Matrix,
    val tValid: List<Double>,
    val xTest: Matrix,
    val tTest: List<Double>,
    val featureNames: List<String>,
)

fun loadBoston(csvFile: File): AnyFrame = DataFrame.readCSV(csvFile)

fun prepareBoston(
    csvFile: File,
    testSize: Double,
    validationSize: Double,
    seed: Int,
): BostonDataset {
    val df = removeOutliers(loadBoston(csvFile), FEATURES + TARGET, OUTLIER_THRESHOLD)
    val x = dataFrameOf(FEATURES.map { df[it].convertToDouble() })
    val t = df[TARGET].values().map { (it as Number).toDouble() }
    val outer = splitTrainTest(x, t, testSize, seed)
    val inner = splitTrainTest(outer.xTrain, outer.tTrain, validationSize, seed)
    val scaler = fitPolynomialScaler(inner.xTrain)
    return BostonDataset(
        xTrain = scaler.transform(inner.xTrain),
        tTrain = inner.tTrain,
        xValid = scaler.transform(inner.xTest),
        tValid = inner.tTest,
        xTest = scaler.transform(outer.xTest),
        tTest = outer.tTest,
        featureNames = scaler.featureNames,
    )
}
