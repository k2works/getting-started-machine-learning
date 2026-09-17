package chapter10

import chapter02.TrainTestSplit
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.tribuo.classification.sgd.linear.LogisticRegressionTrainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TribuoClassifierTest {
    private fun twoSpeciesSplit(): TrainTestSplit<String> {
        val (x, t) = twoSpecies()
        return TrainTestSplit(
            xTrain = x,
            xTest =
                dataFrameOf(
                    "がく片幅" to listOf(0.4, 0.4),
                    "花弁幅" to listOf(0.13, 0.83),
                ),
            tTrain = t,
            tTest = listOf("setosa", "virginica"),
        )
    }

    @Test
    fun `Tribuoのロジスティック回帰とランダムフォレストも同じ関数で評価できる`() {
        val models: List<Classifier> =
            listOf(
                TribuoClassifier(LogisticRegressionTrainer()),
                tribuoRandomForest(nEstimators = 10, maxDepth = null, seed = 0L),
            )

        val scores = models.map { evaluate(it, twoSpeciesSplit()) }

        assertEquals(List(2) { Score(train = 1.0, test = 1.0) }, scores)
    }

    @Test
    fun `学習する前に予測するとエラーになる`() {
        val error =
            assertFailsWith<IllegalStateException> {
                TribuoClassifier(LogisticRegressionTrainer()).predict(dataFrameOf("花弁幅" to listOf(0.1)))
            }

        assertEquals("fit で学習してから predict を呼んでください", error.message)
    }
}
