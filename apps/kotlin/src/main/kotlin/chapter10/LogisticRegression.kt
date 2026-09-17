package chapter10

import org.jetbrains.kotlinx.dataframe.AnyFrame
import kotlin.math.exp
import kotlin.math.ln

fun softmax(z: DoubleArray): DoubleArray {
    val max = z.max()
    val exps = z.map { exp(it - max) }
    val total = exps.sum()
    return exps.map { it / total }.toDoubleArray()
}

private const val EPSILON = 1e-12

fun crossEntropy(
    probabilities: List<DoubleArray>,
    targets: List<Int>,
): Double = -probabilities.indices.sumOf { i -> ln(probabilities[i][targets[i]] + EPSILON) } / probabilities.size

fun AnyFrame.toRows(): List<DoubleArray> {
    val columns = columnNames().map { name -> this[name].values().map { (it as Number).toDouble() } }
    return List(rowsCount()) { row -> DoubleArray(columns.size) { columns[it][row] } }
}

class LogisticRegression(
    private val learningRate: Double = 1.0,
    private val epochs: Int = 5000,
) : Classifier {
    var classes: List<String> = emptyList()
        private set

    // weights[特徴量][品種]
    var weights: Array<DoubleArray> = emptyArray()
        private set

    var bias: DoubleArray = DoubleArray(0)
        private set

    var losses: List<Double> = emptyList()
        private set

    private fun scores(row: DoubleArray): DoubleArray =
        DoubleArray(classes.size) { k -> row.indices.sumOf { f -> row[f] * weights[f][k] } + bias[k] }

    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): LogisticRegression {
        val rows = x.toRows()
        classes = t.distinct().sorted()
        val targets = t.map { classes.indexOf(it) }
        weights = Array(x.columnsCount()) { DoubleArray(classes.size) }
        bias = DoubleArray(classes.size)
        val recorded = mutableListOf<Double>()
        repeat(epochs) {
            val probabilities = rows.map { softmax(scores(it)) }
            recorded += crossEntropy(probabilities, targets)
            // 確率 − 正解（正解の品種だけ 1 を引く）
            val errors = probabilities.mapIndexed { i, p -> p.copyOf().also { it[targets[i]] -= 1.0 } }
            for (k in classes.indices) {
                for (f in weights.indices) {
                    weights[f][k] -= learningRate * rows.indices.sumOf { i -> rows[i][f] * errors[i][k] } / rows.size
                }
                bias[k] -= learningRate * errors.sumOf { it[k] } / rows.size
            }
        }
        losses = recorded
        return this
    }

    override fun predict(x: AnyFrame): List<String> {
        check(classes.isNotEmpty()) { "fit で学習してから predict を呼んでください" }
        return x.toRows().map { row ->
            val scores = scores(row)
            classes[scores.indices.maxBy { scores[it] }]
        }
    }
}
