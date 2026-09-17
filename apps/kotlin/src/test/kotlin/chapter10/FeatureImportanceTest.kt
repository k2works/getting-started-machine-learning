package chapter10

import chapter03.DecisionTree
import chapter03.Leaf
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.test.Test
import kotlin.test.assertEquals

class TreeImportancesTest {
    @Test
    fun `分割しない木はすべての特徴量の重要度が0`() {
        val x =
            dataFrameOf(
                "がく片幅" to listOf(0.3, 0.5),
                "花弁幅" to listOf(0.1, 0.2),
            )
        val t = listOf("setosa", "setosa")

        assertEquals(mapOf("がく片幅" to 0.0, "花弁幅" to 0.0), treeImportances(Leaf(label = "setosa"), x, t))
    }

    @Test
    fun `1回だけ分割する木は分割に使った特徴量の重要度が1`() {
        val x =
            dataFrameOf(
                "がく片幅" to listOf(0.3, 0.5, 0.4, 0.6),
                "花弁幅" to listOf(0.1, 0.2, 0.8, 0.9),
            )
        val t = listOf("setosa", "setosa", "virginica", "virginica")
        val tree = checkNotNull(DecisionTree().fit(x, t).tree)

        assertEquals(mapOf("がく片幅" to 0.0, "花弁幅" to 1.0), treeImportances(tree, x, t))
    }

    @Test
    fun `分割で減った不純度を件数で重み付けして割合にする`() {
        val x =
            dataFrameOf(
                "花弁長さ" to listOf(0.1, 0.2, 0.3, 0.8, 0.7, 0.9),
                "花弁幅" to listOf(0.1, 0.1, 0.1, 0.2, 0.9, 0.9),
            )
        val t = listOf("setosa", "setosa", "setosa", "versicolor", "virginica", "virginica")
        val tree = checkNotNull(DecisionTree().fit(x, t).tree)

        val importances = treeImportances(tree, x, t)

        assertEquals(7.0 / 11, importances.getValue("花弁長さ"), absoluteTolerance = 1e-12)
        assertEquals(4.0 / 11, importances.getValue("花弁幅"), absoluteTolerance = 1e-12)
    }
}

class ForestImportancesTest {
    @Test
    fun `木が1本なら学習に使った行でのその木の重要度と一致する`() {
        val x =
            dataFrameOf(
                "がく片幅" to listOf(0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4),
                "花弁幅" to listOf(0.1, 0.12, 0.14, 0.16, 0.8, 0.82, 0.84, 0.86),
            )
        val t = List(4) { "setosa" } + List(4) { "virginica" }
        val forest = RandomForest(nEstimators = 1, maxFeatures = 2, seed = 0).fit(x, t)
        val fitted = forest.trees.single()

        val expected = treeImportances(checkNotNull(fitted.model.tree), x[fitted.rows], t.slice(fitted.rows))

        assertEquals(expected, forestImportances(forest, x, t))
    }
}
