package chapter03

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.AnyRow
import org.jetbrains.kotlinx.dataframe.api.rows
import java.util.Locale

fun gini(labels: List<String>): Double {
    val total = labels.size.toDouble()
    return 1.0 -
        labels
            .groupingBy { it }
            .eachCount()
            .values
            .sumOf { (it / total) * (it / total) }
}

data class Split(
    val feature: String,
    val threshold: Double,
    val impurity: Double,
)

fun bestSplit(
    x: AnyFrame,
    t: List<String>,
): Split? {
    if (gini(t) == 0.0) return null
    var best: Split? = null
    for (feature in x.columnNames()) {
        val pairs =
            x[feature]
                .values()
                .map { (it as Number).toDouble() }
                .zip(t)
                .sortedBy { it.first }
        val values = pairs.map { it.first }
        val labels = pairs.map { it.second }
        for (i in 1 until pairs.size) {
            if (values[i] == values[i - 1]) continue
            val left = labels.subList(0, i)
            val right = labels.subList(i, labels.size)
            val impurity = (left.size * gini(left) + right.size * gini(right)) / pairs.size
            if (best == null || impurity < best.impurity) {
                best = Split(feature = feature, threshold = (values[i - 1] + values[i]) / 2, impurity = impurity)
            }
        }
    }
    return best
}

sealed interface Tree

data class Leaf(
    val label: String,
) : Tree

data class Node(
    val split: Split,
    val left: Tree,
    val right: Tree,
) : Tree

fun buildTree(
    x: AnyFrame,
    t: List<String>,
    maxDepth: Int?,
): Tree {
    val split =
        (if (maxDepth == 0) null else bestSplit(x, t)) ?: return Leaf(
            t
                .groupingBy { it }
                .eachCount()
                .maxBy { it.value }
                .key,
        )
    val goesLeft = x[split.feature].values().map { (it as Number).toDouble() <= split.threshold }
    val left = goesLeft.indices.filter { goesLeft[it] }
    val right = goesLeft.indices.filterNot { goesLeft[it] }
    val childDepth = maxDepth?.minus(1)
    return Node(
        split = split,
        left = buildTree(x[left], t.slice(left), childDepth),
        right = buildTree(x[right], t.slice(right), childDepth),
    )
}

fun predictOne(
    tree: Tree,
    row: AnyRow,
): String =
    when (tree) {
        is Leaf -> {
            tree.label
        }

        is Node -> {
            if ((row[tree.split.feature] as Number).toDouble() <= tree.split.threshold) {
                predictOne(tree.left, row)
            } else {
                predictOne(tree.right, row)
            }
        }
    }

class DecisionTree(
    private val maxDepth: Int? = null,
) {
    var tree: Tree? = null
        private set

    fun fit(
        x: AnyFrame,
        t: List<String>,
    ): DecisionTree {
        tree = buildTree(x, t, maxDepth)
        return this
    }

    fun predict(x: AnyFrame): List<String> {
        val fitted = checkNotNull(tree) { "fit で学習してから predict を呼んでください" }
        return x.rows().map { predictOne(fitted, it) }
    }
}

fun formatTree(
    tree: Tree,
    indent: String = "",
): String =
    when (tree) {
        is Leaf -> {
            "$indent${tree.label}"
        }

        is Node -> {
            val threshold = "%.4f".format(Locale.ROOT, tree.split.threshold)
            listOf(
                "$indent${tree.split.feature} <= $threshold",
                formatTree(tree.left, "$indent  "),
                "$indent${tree.split.feature} > $threshold",
                formatTree(tree.right, "$indent  "),
            ).joinToString("\n")
        }
    }
