package chapter08

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.AnyRow
import org.jetbrains.kotlinx.dataframe.api.rows
import java.io.Serializable

fun weightedGini(
    labels: List<Int>,
    weights: List<Double>,
): Double {
    val total = weights.sum()
    return 1.0 -
        labels.indices
            .groupBy({ labels[it] }, { weights[it] })
            .values
            .sumOf { (it.sum() / total) * (it.sum() / total) }
}

/** クラスの件数に反比例する重み（件数 / (クラスの数 × そのクラスの件数)）を 1 件ごとに求める */
fun balancedWeights(t: List<Int>): List<Double> {
    val counts = t.groupingBy { it }.eachCount()
    return t.map { t.size.toDouble() / (counts.size * counts.getValue(it)) }
}

enum class ClassWeight {
    NONE,
    BALANCED,
}

sealed interface TreeNode : Serializable

data class LeafNode(
    val label: Int,
) : TreeNode {
    private companion object {
        private const val serialVersionUID: Long = 1
    }
}

data class SplitNode(
    val feature: String,
    val threshold: Double,
    val left: TreeNode,
    val right: TreeNode,
) : TreeNode {
    private companion object {
        private const val serialVersionUID: Long = 1
    }
}

private data class Candidate(
    val feature: String,
    val threshold: Double,
    val impurity: Double,
)

private fun bestSplit(
    x: AnyFrame,
    t: List<Int>,
    w: List<Double>,
): Candidate? {
    if (weightedGini(t, w) == 0.0) return null
    var best: Candidate? = null
    for (feature in x.columnNames()) {
        val order =
            x[feature]
                .values()
                .map { (it as Number).toDouble() }
                .withIndex()
                .sortedBy { it.value }
        val values = order.map { it.value }
        val labels = order.map { t[it.index] }
        val weights = order.map { w[it.index] }
        for (i in 1 until order.size) {
            if (values[i] == values[i - 1]) continue
            val leftLabels = labels.subList(0, i)
            val rightLabels = labels.subList(i, labels.size)
            val left = weights.subList(0, i)
            val right = weights.subList(i, weights.size)
            val impurity = (left.sum() * weightedGini(leftLabels, left) + right.sum() * weightedGini(rightLabels, right)) / weights.sum()
            if (best == null || impurity < best.impurity) {
                best = Candidate(feature = feature, threshold = (values[i - 1] + values[i]) / 2, impurity = impurity)
            }
        }
    }
    return best
}

private fun weightedMajority(
    t: List<Int>,
    w: List<Double>,
): Int =
    t.indices
        .groupBy({ t[it] }, { w[it] })
        .mapValues { it.value.sum() }
        .maxBy { it.value }
        .key

private fun buildNode(
    x: AnyFrame,
    t: List<Int>,
    w: List<Double>,
    maxDepth: Int?,
): TreeNode {
    val split = (if (maxDepth == 0) null else bestSplit(x, t, w)) ?: return LeafNode(weightedMajority(t, w))
    val goesLeft = x[split.feature].values().map { (it as Number).toDouble() <= split.threshold }
    val left = goesLeft.indices.filter { goesLeft[it] }
    val right = goesLeft.indices.filterNot { goesLeft[it] }
    val childDepth = maxDepth?.minus(1)
    return SplitNode(
        feature = split.feature,
        threshold = split.threshold,
        left = buildNode(x[left], t.slice(left), w.slice(left), childDepth),
        right = buildNode(x[right], t.slice(right), w.slice(right), childDepth),
    )
}

private fun predictOne(
    node: TreeNode,
    row: AnyRow,
): Int =
    when (node) {
        is LeafNode -> {
            node.label
        }

        is SplitNode -> {
            if ((row[node.feature] as Number).toDouble() <= node.threshold) predictOne(node.left, row) else predictOne(node.right, row)
        }
    }

data class DecisionTreeClassifier(
    val maxDepth: Int?,
    val classWeight: ClassWeight,
) {
    fun fit(
        x: AnyFrame,
        t: List<Int>,
    ): FittedDecisionTree {
        val weights =
            when (classWeight) {
                ClassWeight.NONE -> t.map { 1.0 }
                ClassWeight.BALANCED -> balancedWeights(t)
            }
        return FittedDecisionTree(buildNode(x, t, weights, maxDepth))
    }
}

data class FittedDecisionTree(
    val root: TreeNode,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1
    }

    fun predict(x: AnyFrame): List<Int> = x.rows().map { predictOne(root, it) }
}
