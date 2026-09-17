package chapter07

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.tribuo.regression.evaluation.RegressionEvaluator
import org.tribuo.regression.slm.SparseLinearModel
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

private fun noisyDataset(): Pair<AnyFrame, List<Double>> {
    val random = java.util.Random(0)
    val a = List(30) { random.nextDouble() * 10 }
    val b = List(30) { random.nextDouble() * 10 }
    val c = List(30) { random.nextDouble() * 10 }
    val t = List(30) { 4.0 + 1.5 * a[it] - 0.5 * b[it] + 2.0 * c[it] + random.nextGaussian() }
    return dataFrameOf("a" to a, "b" to b, "c" to c) to t
}

class ToRegressionDatasetTest {
    @Test
    fun `データフレームの行を数値の正解ラベル付きの事例に変換する`() {
        val x = dataFrameOf("SNS1" to listOf(100.0, 200.0), "actor" to listOf(9000.0, 9500.0))

        val dataset = toRegressionDataset(x, listOf(9200.0, 9800.0))

        assertEquals(2, dataset.size())
        assertEquals(setOf("SNS1", "actor"), dataset.featureIDMap.map { it.name }.toSet())
        assertEquals(listOf(9200.0, 9800.0), dataset.map { it.output.values.single() })
    }
}

class TribuoLinearRegressionTest {
    @Test
    fun `SLMTrainerの予測は自作の線形回帰の予測と一致する`() {
        val (x, t) = noisyDataset()

        val model = trainTribuoLinearRegression(x, t)

        assertDoubles(fitLinearRegression(x, t).predict(x), predictWithTribuo(model, x))
    }

    @Test
    fun `Tribuoの重みは正規化した空間の値で元の単位に戻すと自作の係数と一致する`() {
        val (x, t) = noisyDataset()
        val weights = (trainTribuoLinearRegression(x, t) as SparseLinearModel).weights.values.single()

        val coefficients = fitLinearRegression(x, t).coefficients

        x.columnNames().forEachIndexed { i, name ->
            val restored = weights.get(i) * centeredNorm(t) / centeredNorm(x[name].values().map { (it as Number).toDouble() })
            assertEquals(coefficients.getValue(name), restored, absoluteTolerance = 1e-9)
        }
    }

    @Test
    fun `Tribuoの評価器のMAEとRMSEとR2は自作の評価指標と一致する`() {
        val (x, t) = noisyDataset()
        val model = trainTribuoLinearRegression(x, t)
        val y = predictWithTribuo(model, x)

        val evaluation = RegressionEvaluator().evaluate(model, toRegressionDataset(x, t))

        assertEquals(meanAbsoluteError(t, y), evaluation.mae().values.single(), absoluteTolerance = 1e-9)
        assertEquals(rootMeanSquaredError(t, y), evaluation.rmse().values.single(), absoluteTolerance = 1e-9)
        assertEquals(r2Score(t, y), evaluation.r2().values.single(), absoluteTolerance = 1e-9)
    }
}

private fun centeredNorm(values: List<Double>): Double {
    val mean = values.average()
    return sqrt(values.sumOf { (it - mean) * (it - mean) })
}
