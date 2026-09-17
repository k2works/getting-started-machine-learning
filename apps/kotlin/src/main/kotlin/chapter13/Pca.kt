package chapter13

import chapter07.Matrix
import org.tribuo.math.la.DenseMatrix
import kotlin.math.abs
import kotlin.math.sign

fun columnMeans(x: Matrix): List<Double> = x.columns.map { it.average() }

private fun center(
    x: Matrix,
    means: List<Double>,
): Matrix = Matrix(x.rows.map { row -> row.zip(means) { value, mean -> value - mean } })

fun covarianceMatrix(x: Matrix): Matrix {
    val centered = center(x, columnMeans(x))
    val n = x.rows.size
    return Matrix((centered.transpose() * centered).rows.map { row -> row.map { it / (n - 1) } })
}

/** 固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の要素が正になるようにそろえる */
fun normalizeSigns(components: Matrix): Matrix =
    Matrix(
        components.rows.map { row ->
            val largest = row.maxBy { abs(it) }
            row.map { it * sign(largest) }
        },
    )

fun fitPca(
    x: Matrix,
    nComponents: Int,
): PcaModel {
    val covariance = DenseMatrix.createDenseMatrix(covarianceMatrix(x).rows.map { it.toDoubleArray() }.toTypedArray())
    val eigen = covariance.eigenDecomposition().orElseThrow()
    val eigenvalues = eigen.eigenvalues().toArray().toList()
    val components = (0 until nComponents).map { eigen.getEigenVector(it).toArray().toList() }
    return PcaModel(
        mean = columnMeans(x),
        components = normalizeSigns(Matrix(components)),
        explainedVariance = eigenvalues.take(nComponents),
        explainedVarianceRatio = eigenvalues.take(nComponents).map { it / eigenvalues.sum() },
    )
}

fun transform(
    model: PcaModel,
    x: Matrix,
): Matrix = center(x, model.mean) * model.components.transpose()

fun componentsNeeded(
    ratios: List<Double>,
    threshold: Double,
): Int = ratios.runningReduce(Double::plus).indexOfFirst { it >= threshold } + 1

fun topLoadings(
    component: List<Double>,
    columns: List<String>,
    k: Int,
): List<Pair<String, Double>> = columns.zip(component).sortedByDescending { (_, value) -> abs(value) }.take(k)
