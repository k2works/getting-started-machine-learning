package chapter12

import chapter07.Matrix
import com.oracle.labs.mlrg.olcut.config.PropertyException
import org.tribuo.regression.slm.ElasticNetCDTrainer
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private fun sparseDataset(): Pair<Matrix, List<Double>> {
    val random = Random(0)
    val x = Matrix(List(50) { List(4) { random.nextGaussian() } })
    val t = x.rows.map { row -> 3.0 * row[0] - 2.0 * row[1] + 0.1 * random.nextGaussian() }
    return x to t
}

class TribuoLassoTest {
    @Test
    fun `ラッソ回帰では予測に役立たない特徴量の係数が0になる`() {
        val (x, t) = sparseDataset()

        val model = fitLasso(x, t, alpha = 0.5)

        assertEquals(listOf("noise1", "noise2"), zeroCoefficientNames(model.coefficients, listOf("x1", "x2", "noise1", "noise2")))
    }
}

class TribuoRidgeTest {
    @Test
    fun `ElasticNetCDTrainerはl1Ratioが0のリッジ回帰を受け付けない`() {
        val error = assertFailsWith<PropertyException> { ElasticNetCDTrainer(0.5, 0.0) }

        assertEquals("L1 Ratio must be between 0 and 1. Found value 0.0", error.message?.substringAfter(", "))
    }

    @Test
    fun `l1Ratioを下限まで小さくしalphaを件数で割ると自作のリッジ回帰と同じ係数と切片になる`() {
        val (x, t) = randomDataset()

        val model = fitRidgeWithTribuo(x, t, alpha = 10.0)
        val expected = fitRidge(x, t, alpha = 10.0)

        assertDoubles(expected.coefficients, model.coefficients, tolerance = 1e-6)
        assertEquals(expected.intercept, model.intercept, absoluteTolerance = 1e-6)
    }
}
