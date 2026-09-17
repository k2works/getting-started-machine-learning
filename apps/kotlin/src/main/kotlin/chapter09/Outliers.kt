package chapter09

import chapter02.TrainTestSplit
import kotlin.math.ceil
import kotlin.math.floor

private const val FIRST_QUARTILE = 0.25
private const val THIRD_QUARTILE = 0.75

fun quantile(
    values: List<Double>,
    q: Double,
): Double {
    val sorted = values.sorted()
    val position = (sorted.size - 1) * q
    val lower = floor(position).toInt()
    val upper = ceil(position).toInt()
    return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
}

fun iqrOutliers(
    values: List<Double>,
    k: Double = 1.5,
): List<Boolean> {
    val q1 = quantile(values, FIRST_QUARTILE)
    val q3 = quantile(values, THIRD_QUARTILE)
    val iqr = q3 - q1
    return values.map { it < q1 - k * iqr || it > q3 + k * iqr }
}

fun removeTargetOutliers(split: TrainTestSplit<Double>): TrainTestSplit<Double> {
    val outliers = iqrOutliers(split.tTrain)
    val keep = outliers.indices.filterNot { outliers[it] }
    return split.copy(xTrain = split.xTrain[keep], tTrain = split.tTrain.slice(keep))
}
