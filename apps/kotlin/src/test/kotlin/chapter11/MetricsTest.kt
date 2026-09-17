package chapter11

import chapter07.meanAbsoluteError
import chapter07.rootMeanSquaredError
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ConfusionMatrixTest {
    @Test
    fun `正例と負例の予測の当たり外れを数える`() {
        val actual = listOf(1, 1, 1, 0, 0)
        val predicted = listOf(1, 1, 0, 1, 0)

        assertEquals(ConfusionMatrix(tp = 2, fp = 1, fn = 1, tn = 1), confusionMatrix(actual, predicted, positive = 1))
    }

    @Test
    fun `どちらのラベルを正例とするかで数え方が変わる`() {
        val actual = listOf(1, 1, 1, 0, 0, 0)
        val predicted = listOf(1, 0, 0, 0, 0, 1)

        assertEquals(ConfusionMatrix(tp = 2, fp = 2, fn = 1, tn = 1), confusionMatrix(actual, predicted, positive = 0))
    }

    @Test
    fun `正解と予測の件数が違えばエラーになる`() {
        assertFailsWith<IllegalArgumentException> { confusionMatrix(listOf(1, 0, 1), listOf(1, 0), positive = 1) }
    }
}

class PrecisionRecallF1Test {
    private val cm = ConfusionMatrix(tp = 3, fp = 1, fn = 2, tn = 4)

    @Test
    fun `適合率は正例と予測したうち本当に正例だった割合`() {
        assertEquals(0.75, precision(cm), absoluteTolerance = 1e-12)
    }

    @Test
    fun `再現率は本当の正例のうち正例と予測できた割合`() {
        assertEquals(0.6, recall(cm), absoluteTolerance = 1e-12)
    }

    @Test
    fun `F値は適合率と再現率の調和平均`() {
        assertEquals(2 * 0.75 * 0.6 / (0.75 + 0.6), f1Score(cm), absoluteTolerance = 1e-12)
    }

    @Test
    fun `正例を一件も当てられなければ適合率と再現率とF値は0`() {
        val missed = ConfusionMatrix(tp = 0, fp = 0, fn = 3, tn = 5)

        assertEquals(Triple(0.0, 0.0, 0.0), Triple(precision(missed), recall(missed), f1Score(missed)))
    }
}

class RegressionMetricsTest {
    @Test
    fun `誤差の2乗の平均と平方根と絶対値の平均を求める`() {
        val actual = listOf(3.0, 5.0, 8.0)
        val predicted = listOf(2.0, 5.0, 10.0)

        assertEquals(5.0 / 3, meanSquaredError(actual, predicted), absoluteTolerance = 1e-12)
        assertEquals(sqrt(5.0 / 3), rootMeanSquaredError(actual, predicted), absoluteTolerance = 1e-12)
        assertEquals(1.0, meanAbsoluteError(actual, predicted), absoluteTolerance = 1e-12)
    }

    @Test
    fun `大きく外れた予測があるとRMSEはMAEより大きく増える`() {
        val actual = listOf(3.0, 5.0, 8.0, 10.0)
        val predicted = listOf(2.0, 5.0, 10.0, 30.0)

        assertEquals(sqrt(101.25), rootMeanSquaredError(actual, predicted), absoluteTolerance = 1e-12)
        assertEquals(5.75, meanAbsoluteError(actual, predicted), absoluteTolerance = 1e-12)
    }

    @Test
    fun `MSEも正解と予測の件数が違えばエラーになる`() {
        assertFailsWith<IllegalArgumentException> { meanSquaredError(listOf(1.0, 2.0), listOf(1.0)) }
    }
}

class ClassificationMetricTest {
    @Test
    fun `正解率は正解と予測が一致した割合`() {
        assertEquals(0.75, accuracy(listOf(1, 0, 1, 0), listOf(1, 1, 1, 0)), absoluteTolerance = 1e-12)
    }

    @Test
    fun `正解率も正解と予測の件数が違えばエラーになる`() {
        assertFailsWith<IllegalArgumentException> { accuracy(listOf(1, 0, 1), listOf(1, 0)) }
    }

    @Test
    fun `混同行列から求める指標を正解と予測から求める評価関数に変える`() {
        val actual = listOf(1, 1, 1, 0, 0)
        val predicted = listOf(1, 0, 0, 1, 0)

        val precisionMetric = classificationMetric(::precision, positive = 1)
        val recallMetric = classificationMetric(::recall, positive = 1)

        assertEquals(0.5, precisionMetric(actual, predicted), absoluteTolerance = 1e-12)
        assertEquals(1.0 / 3, recallMetric(actual, predicted), absoluteTolerance = 1e-12)
    }
}
