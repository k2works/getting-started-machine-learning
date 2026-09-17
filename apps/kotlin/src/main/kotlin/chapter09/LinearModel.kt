package chapter09

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.tribuo.math.la.DenseMatrix
import org.tribuo.math.la.DenseVector

internal fun AnyFrame.toRows(): Array<DoubleArray> =
    Array(rowsCount()) { row -> DoubleArray(columnsCount()) { column -> (columns()[column][row] as Number).toDouble() } }

data class LinearModel(
    val intercept: Double,
    val weights: List<Double>,
) {
    fun predict(rows: Array<DoubleArray>): List<Double> = rows.map { row -> intercept + row.indices.sumOf { weights[it] * row[it] } }
}

fun fitLinearRegression(
    rows: Array<DoubleArray>,
    t: List<Double>,
): LinearModel {
    val design = DenseMatrix.createDenseMatrix(Array(rows.size) { doubleArrayOf(1.0) + rows[it] })
    val transposed = design.transpose()
    val cholesky =
        transposed.matrixMultiply(design).choleskyFactorization().orElseThrow {
            IllegalArgumentException("特徴量の列が互いに独立でないため、正規方程式を解けません")
        }
    val beta = cholesky.solve(transposed.leftMultiply(DenseVector.createDenseVector(t.toDoubleArray()))).toArray()
    return LinearModel(intercept = beta.first(), weights = beta.drop(1))
}

fun rSquared(
    actual: List<Double>,
    predicted: List<Double>,
): Double {
    val mean = actual.average()
    val residual = actual.zip(predicted).sumOf { (a, p) -> (a - p) * (a - p) }
    val total = actual.sumOf { (it - mean) * (it - mean) }
    return 1 - residual / total
}
