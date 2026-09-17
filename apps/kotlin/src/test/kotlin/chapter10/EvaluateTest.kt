package chapter10

import chapter02.TrainTestSplit
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.test.Test
import kotlin.test.assertEquals

private class AlwaysSetosa : Classifier {
    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): AlwaysSetosa = this

    override fun predict(x: AnyFrame): List<String> = List(x.rowsCount()) { "setosa" }
}

private fun smallSplit(): TrainTestSplit<String> =
    TrainTestSplit(
        xTrain = dataFrameOf("花弁幅" to listOf(0.1, 0.2, 0.8, 0.9)),
        xTest = dataFrameOf("花弁幅" to listOf(0.15, 0.25)),
        tTrain = listOf("setosa", "setosa", "virginica", "virginica"),
        tTest = listOf("setosa", "setosa"),
    )

class EvaluateTest {
    @Test
    fun `学習させてから訓練データとテストデータの正解率を求める`() {
        assertEquals(Score(train = 0.5, test = 1.0), evaluate(AlwaysSetosa(), smallSplit()))
    }

    @Test
    fun `第3章の決定木と自作のモデルを同じ関数で評価できる`() {
        val models: List<Classifier> =
            listOf(
                DecisionTreeClassifier(maxDepth = 1),
                LogisticRegression(),
                RandomForest(nEstimators = 5, maxFeatures = 1, seed = 0),
            )

        val scores = models.map { evaluate(it, smallSplit()) }

        assertEquals(List(3) { Score(train = 1.0, test = 1.0) }, scores)
    }
}
