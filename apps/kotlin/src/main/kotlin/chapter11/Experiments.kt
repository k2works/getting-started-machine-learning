package chapter11

import chapter07.meanAbsoluteError
import chapter07.rootMeanSquaredError
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.File

const val N_SPLITS = 5
const val SEED = 0
private const val TREE_DEPTH = 2
private const val SURVIVED = "1"

val SURVIVED_METRICS: Map<String, Metric<String>> =
    mapOf(
        "正解率" to ::accuracy,
        "適合率" to classificationMetric(::precision, positive = SURVIVED),
        "再現率" to classificationMetric(::recall, positive = SURVIVED),
        "F値" to classificationMetric(::f1Score, positive = SURVIVED),
    )

val CINEMA_METRICS: Map<String, Metric<Double>> =
    mapOf(
        "RMSE" to ::rootMeanSquaredError,
        "MAE" to ::meanAbsoluteError,
    )

fun <T> evaluate(
    makeModel: () -> Model<T>,
    x: AnyFrame,
    t: List<T>,
    metrics: Map<String, Metric<T>>,
): Map<String, Double> {
    val folds = kFold(nSamples = x.rowsCount(), nSplits = N_SPLITS, seed = SEED)
    return metrics.mapValues { (_, metric) -> crossValidate(makeModel, x, t, folds, metric).average() }
}

fun evaluateSurvived(csvFile: File): Map<String, Double> {
    val (x, t) = prepareSurvived(DataFrame.readCSV(csvFile))
    return evaluate({ DecisionTreeModel(maxDepth = TREE_DEPTH) }, x, t, SURVIVED_METRICS)
}

fun evaluateCinema(csvFile: File): Map<String, Double> {
    val (x, t) = prepareCinema(DataFrame.readCSV(csvFile))
    return evaluate(::LinearRegressionModel, x, t, CINEMA_METRICS)
}
