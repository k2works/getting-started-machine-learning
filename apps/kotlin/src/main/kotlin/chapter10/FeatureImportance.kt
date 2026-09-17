package chapter10

import chapter03.Leaf
import chapter03.Node
import chapter03.Tree
import chapter03.gini
import org.jetbrains.kotlinx.dataframe.AnyFrame

private fun impurityDecreases(
    tree: Tree,
    x: AnyFrame,
    t: List<String>,
): List<Pair<String, Double>> =
    when (tree) {
        is Leaf -> {
            emptyList()
        }

        is Node -> {
            val split = tree.split
            val goesLeft = x[split.feature].values().map { (it as Number).toDouble() <= split.threshold }
            val left = goesLeft.indices.filter { goesLeft[it] }
            val right = goesLeft.indices.filterNot { goesLeft[it] }
            listOf(split.feature to t.size * (gini(t) - split.impurity)) +
                impurityDecreases(tree.left, x[left], t.slice(left)) +
                impurityDecreases(tree.right, x[right], t.slice(right))
        }
    }

fun treeImportances(
    tree: Tree,
    x: AnyFrame,
    t: List<String>,
): Map<String, Double> {
    val decreases = impurityDecreases(tree, x, t)
    val totals = x.columnNames().associateWith { feature -> decreases.filter { it.first == feature }.sumOf { it.second } }
    return normalize(totals)
}

private fun normalize(totals: Map<String, Double>): Map<String, Double> {
    val total = totals.values.sum()
    return if (total == 0.0) totals else totals.mapValues { it.value / total }
}

fun forestImportances(
    forest: RandomForest,
    x: AnyFrame,
    t: List<String>,
): Map<String, Double> {
    val perTree =
        forest.trees.map { fitted ->
            val tree = checkNotNull(fitted.model.tree)
            treeImportances(tree, x[fitted.rows].selectColumns(fitted.columns), t.slice(fitted.rows))
        }
    val totals = x.columnNames().associateWith { feature -> perTree.sumOf { it[feature] ?: 0.0 } / forest.trees.size }
    return normalize(totals)
}
