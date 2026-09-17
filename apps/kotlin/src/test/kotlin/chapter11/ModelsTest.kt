package chapter11

import chapter03.DecisionTree
import chapter07.fitLinearRegression
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.test.Test
import kotlin.test.assertEquals

class DecisionTreeModelTest {
    @Test
    fun `第3章の決定木と同じ予測をする`() {
        val x = dataFrameOf("feature" to listOf(0.1, 0.2, 0.3, 0.6, 0.7, 0.9))
        val t = listOf("0", "0", "1", "1", "0", "1")
        val newX = dataFrameOf("feature" to listOf(0.15, 0.35, 0.8))

        val model = DecisionTreeModel(maxDepth = 1)
        model.fit(x, t)

        assertEquals(DecisionTree(maxDepth = 1).fit(x, t).predict(newX), model.predict(newX))
    }
}

class LinearRegressionModelTest {
    @Test
    fun `第7章の線形回帰と同じ予測をする`() {
        val x = dataFrameOf("feature" to listOf(1.0, 2.0, 3.0, 4.0))
        val t = listOf(2.1, 3.9, 6.2, 7.8)
        val newX = dataFrameOf("feature" to listOf(1.5, 5.0))

        val model = LinearRegressionModel()
        model.fit(x, t)

        assertEquals(fitLinearRegression(x, t).predict(newX), model.predict(newX))
    }
}
