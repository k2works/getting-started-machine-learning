package chapter11

import chapter03.predictWithTribuo
import chapter03.toTribuoDataset
import chapter03.trainTribuoTree
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.tribuo.classification.Label
import org.tribuo.classification.evaluation.LabelEvaluator
import org.tribuo.evaluation.KFoldSplitter
import org.tribuo.regression.evaluation.RegressionEvaluator
import kotlin.test.Test
import kotlin.test.assertEquals
import chapter07.predictWithTribuo as predictRegressionWithTribuo
import chapter07.toRegressionDataset as toTribuoRegressionDataset
import chapter07.trainTribuoLinearRegression as trainTribuoRegression

class TribuoLabelEvaluatorTest {
    private val x = dataFrameOf("feature" to listOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0))
    private val t = listOf("0", "0", "1", "0", "0", "1", "1", "0", "1", "1")
    private val model = trainTribuoTree(x, t, maxDepth = 1, minChildWeight = 1.0f)
    private val predicted = predictWithTribuo(model, x)
    private val evaluation = LabelEvaluator().evaluate(model, toTribuoDataset(x, t))
    private val positive = Label("1")

    @Test
    fun `混同行列がTribuoの評価器と一致する`() {
        val cm = confusionMatrix(t, predicted, positive = "1")

        val tribuo = evaluation.confusionMatrix
        assertEquals(
            listOf(tribuo.tp(positive), tribuo.fp(positive), tribuo.fn(positive), tribuo.tn(positive)),
            listOf(cm.tp, cm.fp, cm.fn, cm.tn).map { it.toDouble() },
        )
    }

    @Test
    fun `正解率と適合率と再現率とF値がTribuoの評価器と一致する`() {
        val cm = confusionMatrix(t, predicted, positive = "1")

        assertEquals(evaluation.accuracy(), accuracy(t, predicted), absoluteTolerance = 1e-12)
        assertEquals(evaluation.precision(positive), precision(cm), absoluteTolerance = 1e-12)
        assertEquals(evaluation.recall(positive), recall(cm), absoluteTolerance = 1e-12)
        assertEquals(evaluation.f1(positive), f1Score(cm), absoluteTolerance = 1e-12)
    }

    @Test
    fun `正例を一件も予測しなければTribuoの評価器も適合率と再現率とF値を0にする`() {
        // 深さ 0 の木は、訓練データの多数派の "0" だけを予測する
        val neverPositive = trainTribuoTree(x, List(10) { if (it == 9) "1" else "0" }, maxDepth = 0, minChildWeight = 1.0f)

        val zero = LabelEvaluator().evaluate(neverPositive, toTribuoDataset(x, t))

        assertEquals(listOf(0.0, 0.0, 0.0), listOf(zero.precision(positive), zero.recall(positive), zero.f1(positive)))
    }
}

class TribuoRegressionEvaluatorTest {
    @Test
    fun `MSEはTribuoの評価器のRMSEの2乗と一致する`() {
        val x = dataFrameOf("feature" to listOf(1.0, 2.0, 3.0, 4.0, 5.0, 6.0))
        val t = listOf(1.1, 2.3, 2.8, 4.4, 4.9, 6.2)
        val model = trainTribuoRegression(x, t)

        val rmse =
            RegressionEvaluator()
                .evaluate(model, toTribuoRegressionDataset(x, t))
                .rmse()
                .values
                .single()

        assertEquals(rmse * rmse, meanSquaredError(t, predictRegressionWithTribuo(model, x)), absoluteTolerance = 1e-9)
    }
}

class TribuoKFoldSplitterTest {
    private fun tribuoTestSizes(
        nSamples: Int,
        nSplits: Int,
    ): List<Int> {
        val x = dataFrameOf("feature" to (0 until nSamples).map { it.toDouble() })
        val dataset = toTribuoDataset(x, List(nSamples) { if (it < nSamples / 2) "0" else "1" })
        return KFoldSplitter<Label>(nSplits, 0L)
            .split(dataset, true)
            .asSequence()
            .map { it.test.size() }
            .toList()
    }

    @Test
    fun `分割ごとのテストデータの件数がTribuoのKFoldSplitterと一致する`() {
        for ((nSamples, nSplits) in listOf(10 to 3, 7 to 2, 11 to 4)) {
            assertEquals(
                tribuoTestSizes(nSamples, nSplits),
                kFold(nSamples, nSplits, seed = 0).map { it.test.size },
                "$nSamples 件を $nSplits 分割",
            )
        }
    }
}

/** Tribuo の CART を第 11 章の Model として使うテスト用のアダプター */
internal class TribuoTree(
    private val maxDepth: Int,
) : Model<String> {
    private var model: org.tribuo.Model<Label>? = null

    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ) {
        model = trainTribuoTree(x, t, maxDepth, minChildWeight = 1.0f)
    }

    override fun predict(x: AnyFrame): List<String> = predictWithTribuo(checkNotNull(model), x)
}

class TribuoCrossValidateTest {
    @Test
    fun `同じ分割ならTribuoの評価器で採点した正解率の平均と一致する`() {
        val x = dataFrameOf("feature" to (1..20).map { it * 0.05 })
        val t = (1..20).map { if (it % 3 == 0 || it > 12) "1" else "0" }
        val folds = kFold(nSamples = 20, nSplits = 4, seed = 0)

        val tribuoScores =
            folds.map { fold ->
                val model = trainTribuoTree(x[fold.train], t.slice(fold.train), maxDepth = 1, minChildWeight = 1.0f)
                LabelEvaluator().evaluate(model, toTribuoDataset(x[fold.test], t.slice(fold.test))).accuracy()
            }

        assertEquals(
            tribuoScores.average(),
            crossValidate({
                TribuoTree(maxDepth = 1)
            }, x, t, folds, ::accuracy).average(),
            absoluteTolerance = 1e-12,
        )
    }
}
