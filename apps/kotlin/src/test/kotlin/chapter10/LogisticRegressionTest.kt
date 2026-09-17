package chapter10

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.math.ln
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SoftmaxTest {
    @Test
    fun `値がすべて同じなら確率は均等になる`() {
        assertEquals(listOf(0.25, 0.25, 0.25, 0.25), softmax(doubleArrayOf(0.0, 0.0, 0.0, 0.0)).toList())
    }

    @Test
    fun `値の差が指数の比になる`() {
        val probabilities = softmax(doubleArrayOf(0.0, ln(2.0)))

        assertEquals(1.0 / 3, probabilities[0], absoluteTolerance = 1e-12)
        assertEquals(2.0 / 3, probabilities[1], absoluteTolerance = 1e-12)
    }

    @Test
    fun `大きな値でもあふれずに確率を求める`() {
        assertEquals(listOf(0.5, 0.5), softmax(doubleArrayOf(1000.0, 1000.0)).toList())
    }
}

class LogisticRegressionTest {
    @Test
    fun `1種類のラベルだけを学習するとそのラベルを予測する`() {
        val x = dataFrameOf("花弁幅" to listOf(0.1, 0.2))
        val t = listOf("setosa", "setosa")

        val model = LogisticRegression().fit(x, t)

        assertEquals(listOf("setosa", "setosa"), model.predict(dataFrameOf("花弁幅" to listOf(0.15, 0.9))))
    }

    @Test
    fun `2種類のラベルを境界の左右で予測する`() {
        val x = dataFrameOf("花弁幅" to listOf(0.1, 0.2, 0.8, 0.9))
        val t = listOf("setosa", "setosa", "virginica", "virginica")

        val model = LogisticRegression().fit(x, t)

        assertEquals(listOf("setosa", "virginica"), model.predict(dataFrameOf("花弁幅" to listOf(0.15, 0.85))))
    }

    @Test
    fun `3種類のラベルを2つの特徴量から予測する`() {
        val x =
            dataFrameOf(
                "花弁長さ" to listOf(0.1, 0.2, 0.5, 0.6, 0.5, 0.6),
                "花弁幅" to listOf(0.1, 0.2, 0.1, 0.2, 0.8, 0.9),
            )
        val t = listOf("setosa", "setosa", "versicolor", "versicolor", "virginica", "virginica")

        val model = LogisticRegression().fit(x, t)

        assertEquals(t, model.predict(x))
    }

    @Test
    fun `学習を繰り返すと損失が小さくなる`() {
        val x = dataFrameOf("花弁幅" to listOf(0.1, 0.2, 0.8, 0.9))
        val t = listOf("setosa", "setosa", "virginica", "virginica")

        val model = LogisticRegression(epochs = 100).fit(x, t)

        assertEquals(100, model.losses.size)
        assertTrue(model.losses.last() < model.losses.first())
    }

    @Test
    fun `学習する前に予測するとエラーになる`() {
        val error = assertFailsWith<IllegalStateException> { LogisticRegression().predict(dataFrameOf("花弁幅" to listOf(0.1))) }

        assertEquals("fit で学習してから predict を呼んでください", error.message)
    }
}
