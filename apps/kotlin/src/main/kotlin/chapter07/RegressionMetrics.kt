package chapter07

import kotlin.math.abs
import kotlin.math.sqrt

private fun List<Double>.residuals(y: List<Double>): List<Double> {
    require(size == y.size) { "実測値と予測値の件数が違います" }
    return zip(y) { actual, predicted -> actual - predicted }
}

fun meanAbsoluteError(
    t: List<Double>,
    y: List<Double>,
): Double = t.residuals(y).map { abs(it) }.average()

fun rootMeanSquaredError(
    t: List<Double>,
    y: List<Double>,
): Double = sqrt(t.residuals(y).map { it * it }.average())

fun r2Score(
    t: List<Double>,
    y: List<Double>,
): Double {
    val residual = t.residuals(y).sumOf { it * it }
    val mean = t.average()
    val total = t.sumOf { (it - mean) * (it - mean) }
    return 1 - residual / total
}
