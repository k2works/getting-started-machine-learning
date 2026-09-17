package chapter11

private fun requireSameSize(
    actual: List<*>,
    predicted: List<*>,
) = require(actual.size == predicted.size) { "正解と予測の件数が違います（正解 ${actual.size} 件、予測 ${predicted.size} 件）" }

data class ConfusionMatrix(
    val tp: Int,
    val fp: Int,
    val fn: Int,
    val tn: Int,
)

fun <T> confusionMatrix(
    actual: List<T>,
    predicted: List<T>,
    positive: T,
): ConfusionMatrix {
    requireSameSize(actual, predicted)
    val pairs = actual.zip(predicted) { a, p -> (a == positive) to (p == positive) }
    return ConfusionMatrix(
        tp = pairs.count { it == (true to true) },
        fp = pairs.count { it == (false to true) },
        fn = pairs.count { it == (true to false) },
        tn = pairs.count { it == (false to false) },
    )
}

private fun ratio(
    numerator: Double,
    denominator: Double,
): Double = if (denominator == 0.0) 0.0 else numerator / denominator

fun precision(cm: ConfusionMatrix): Double = ratio(cm.tp.toDouble(), (cm.tp + cm.fp).toDouble())

fun recall(cm: ConfusionMatrix): Double = ratio(cm.tp.toDouble(), (cm.tp + cm.fn).toDouble())

fun f1Score(cm: ConfusionMatrix): Double {
    val p = precision(cm)
    val r = recall(cm)
    return ratio(2 * p * r, p + r)
}

fun meanSquaredError(
    actual: List<Double>,
    predicted: List<Double>,
): Double {
    requireSameSize(actual, predicted)
    return actual.zip(predicted) { a, p -> (p - a) * (p - a) }.average()
}

typealias Metric<T> = (List<T>, List<T>) -> Double

fun <T> accuracy(
    actual: List<T>,
    predicted: List<T>,
): Double {
    requireSameSize(actual, predicted)
    return actual.zip(predicted).count { (a, p) -> a == p }.toDouble() / actual.size
}

fun <T> classificationMetric(
    score: (ConfusionMatrix) -> Double,
    positive: T,
): Metric<T> = { actual, predicted -> score(confusionMatrix(actual, predicted, positive)) }
