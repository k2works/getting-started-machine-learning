package chapter11

import chapter07.meanAbsoluteError
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class KFoldTest {
    @Test
    fun `データをk個のテストデータにほぼ均等に分ける`() {
        val folds = kFold(nSamples = 10, nSplits = 3, seed = 0)

        assertEquals(listOf(4, 3, 3), folds.map { it.test.size })
    }

    @Test
    fun `件数と分割数が変わってもほぼ均等に分ける`() {
        val folds = kFold(nSamples = 7, nSplits = 2, seed = 0)

        assertEquals(listOf(4, 3), folds.map { it.test.size })
    }

    @Test
    fun `どの行もちょうど一度だけテストデータになる`() {
        val folds = kFold(nSamples = 10, nSplits = 3, seed = 0)

        assertEquals((0 until 10).toList(), folds.flatMap { it.test }.sorted())
    }

    @Test
    fun `各分割の訓練データはテストデータ以外のすべての行`() {
        val folds = kFold(nSamples = 10, nSplits = 3, seed = 0)

        for (fold in folds) {
            assertEquals(emptySet(), fold.train.toSet() intersect fold.test.toSet())
            assertEquals((0 until 10).toSet(), fold.train.toSet() + fold.test)
        }
    }

    @Test
    fun `同じシードなら同じ分け方になる`() {
        val first = kFold(nSamples = 10, nSplits = 3, seed = 42)
        val second = kFold(nSamples = 10, nSplits = 3, seed = 42)

        assertEquals(first.map { it.test }, second.map { it.test })
    }

    @Test
    fun `シードが違えば違う分け方になる`() {
        val first = kFold(nSamples = 10, nSplits = 3, seed = 0)
        val second = kFold(nSamples = 10, nSplits = 3, seed = 1)

        assertNotEquals(first.map { it.test }, second.map { it.test })
    }
}

/** 訓練データの正解の平均値を常に予測するテスト用のモデル */
private class MeanModel : Model<Double> {
    private var mean = 0.0

    override fun fit(
        x: AnyFrame,
        t: List<Double>,
    ) {
        mean = t.average()
    }

    override fun predict(x: AnyFrame): List<Double> = List(x.rowsCount()) { mean }
}

class CrossValidateTest {
    private val x = dataFrameOf("feature" to listOf(10, 20, 30, 40))
    private val t = listOf(1.0, 2.0, 3.0, 4.0)
    private val folds =
        listOf(
            Fold(train = listOf(0, 1), test = listOf(2, 3)),
            Fold(train = listOf(2, 3), test = listOf(0, 1)),
        )

    @Test
    fun `分割ごとに訓練データで学習してテストデータを評価する`() {
        val scores = crossValidate(::MeanModel, x, t, folds, ::meanAbsoluteError)

        assertEquals(listOf(2.0, 2.0), scores.toList())
    }

    @Test
    fun `評価関数を差し替えると別の指標で評価する`() {
        val scores = crossValidate(::MeanModel, x, t, folds, ::meanSquaredError)

        assertEquals(listOf(4.25, 4.25), scores.toList())
    }

    @Test
    fun `最初の分割のスコアだけを取り出すなら学習は1回で済む`() {
        var created = 0
        val makeModel = {
            created++
            MeanModel()
        }

        crossValidate(makeModel, x, t, folds, ::meanAbsoluteError).first()

        assertEquals(1, created)
    }

    @Test
    fun `シーケンスからスコアを取り出すたびに学習し直す`() {
        var created = 0
        val makeModel = {
            created++
            MeanModel()
        }
        val scores = crossValidate(makeModel, x, t, folds, ::meanAbsoluteError)

        scores.toList()
        scores.toList()

        assertEquals(4, created)
    }
}
