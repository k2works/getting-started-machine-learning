package chapter12

import chapter07.Matrix
import chapter07.r2Score
import kotlin.math.abs

data class RegularizedModel(
    val coefficients: List<Double>,
    val intercept: Double,
) {
    fun predict(x: Matrix): List<Double> =
        x.rows.map { row ->
            intercept +
                row.zip(coefficients).sumOf { (value, coefficient) -> value * coefficient }
        }
}

fun fitRidge(
    x: Matrix,
    t: List<Double>,
    alpha: Double,
): RegularizedModel {
    val xMeans = x.columns.map { it.average() }
    val tMean = t.average()
    val xc = Matrix(x.rows.map { row -> row.zip(xMeans) { value, mean -> value - mean } })
    val tc = Matrix(t.map { listOf(it - tMean) })
    val coefficients = (xc.transpose() * xc + alpha * identity(xMeans.size)).solve(xc.transpose() * tc).columns.first()
    val intercept = tMean - xMeans.zip(coefficients).sumOf { (mean, coefficient) -> mean * coefficient }
    return RegularizedModel(coefficients = coefficients, intercept = intercept)
}

data class Experiment(
    val alpha: Double,
    val trainScore: Double,
    val validationScore: Double,
    val coefficientAbsSum: Double,
)

fun runRidgeExperiments(
    xTrain: Matrix,
    tTrain: List<Double>,
    xValid: Matrix,
    tValid: List<Double>,
    alphas: List<Double>,
): List<Experiment> =
    alphas.map { alpha ->
        val model = fitRidge(xTrain, tTrain, alpha)
        Experiment(
            alpha = alpha,
            trainScore = r2Score(tTrain, model.predict(xTrain)),
            validationScore = r2Score(tValid, model.predict(xValid)),
            coefficientAbsSum = model.coefficients.sumOf { abs(it) },
        )
    }

fun bestExperiment(experiments: List<Experiment>): Experiment = experiments.maxBy { it.validationScore }

fun zeroCoefficientNames(
    coefficients: List<Double>,
    featureNames: List<String>,
): List<String> = featureNames.zip(coefficients).filter { (_, coefficient) -> coefficient == 0.0 }.map { (name, _) -> name }
