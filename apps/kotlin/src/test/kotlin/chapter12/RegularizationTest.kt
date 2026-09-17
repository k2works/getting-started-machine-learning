package chapter12

import chapter07.Matrix
import chapter07.fitLinearRegression
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toColumn
import java.util.Random
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FitRidgeTest {
    @Test
    fun `alphaが0なら最小二乗法と同じ係数と切片になる`() {
        val x = Matrix(listOf(listOf(1.0), listOf(2.0), listOf(3.0)))
        val t = listOf(3.0, 5.0, 7.0)

        val model = fitRidge(x, t, alpha = 0.0)

        assertEquals(2.0, model.coefficients.single(), absoluteTolerance = 1e-12)
        assertEquals(1.0, model.intercept, absoluteTolerance = 1e-12)
    }

    @Test
    fun `特徴量が2つでも係数と切片を求める`() {
        val x = Matrix(listOf(listOf(1.0, 0.0), listOf(0.0, 1.0), listOf(1.0, 1.0), listOf(2.0, 1.0)))
        val t = x.rows.map { (x1, x2) -> 3.0 * x1 - 1.0 * x2 + 4.0 }

        val model = fitRidge(x, t, alpha = 0.0)

        assertDoubles(listOf(3.0, -1.0), model.coefficients)
        assertEquals(4.0, model.intercept, absoluteTolerance = 1e-9)
    }

    @Test
    fun `alphaを大きくすると係数の絶対値の合計が小さくなる`() {
        val (x, t) = randomDataset()

        val weak = fitRidge(x, t, alpha = 0.1)
        val strong = fitRidge(x, t, alpha = 100.0)

        assertTrue(strong.coefficients.sumOf { abs(it) } < weak.coefficients.sumOf { abs(it) })
    }

    @Test
    fun `alphaが0なら第7章の線形回帰と同じ係数と切片になる`() {
        val (x, t) = randomDataset()
        val frame = dataFrameOf(x.columns.mapIndexed { i, column -> column.toColumn("x$i") })

        val model = fitRidge(x, t, alpha = 0.0)
        val expected = fitLinearRegression(frame, t)

        assertDoubles(expected.coefficients.values.toList(), model.coefficients)
        assertEquals(expected.intercept, model.intercept, absoluteTolerance = 1e-9)
    }
}

class PredictTest {
    @Test
    fun `係数と切片から予測値を計算する`() {
        val model = RegularizedModel(coefficients = listOf(3.0, -1.0), intercept = 4.0)

        val y = model.predict(Matrix(listOf(listOf(1.0, 2.0), listOf(0.0, 0.0))))

        assertDoubles(listOf(5.0, 4.0), y)
    }
}

private fun Matrix.slice(indices: IntRange): Matrix = Matrix(rows.slice(indices))

class RunRidgeExperimentsTest {
    @Test
    fun `正則化の強さごとに1件ずつ実験結果を記録する`() {
        val (x, t) = randomDataset()

        val experiments =
            runRidgeExperiments(x.slice(0..19), t.slice(0..19), x.slice(20..29), t.slice(20..29), alphas = listOf(0.1, 1.0, 10.0))

        assertEquals(listOf(0.1, 1.0, 10.0), experiments.map { it.alpha })
    }

    @Test
    fun `copyで一部を変えた実験結果を作っても元の実験結果は変わらない`() {
        val (x, t) = randomDataset()
        val original = runRidgeExperiments(x.slice(0..19), t.slice(0..19), x.slice(20..29), t.slice(20..29), listOf(1.0)).single()

        val changed = original.copy(alpha = 2.0)

        assertEquals(1.0, original.alpha)
        assertEquals(2.0, changed.alpha)
        assertEquals(original.validationScore, changed.validationScore)
    }
}

private fun experiment(
    alpha: Double,
    validationScore: Double,
): Experiment = Experiment(alpha = alpha, trainScore = 0.9, validationScore = validationScore, coefficientAbsSum = 1.0)

class BestExperimentTest {
    @Test
    fun `検証データの決定係数が最も高い実験を選ぶ`() {
        val experiments = listOf(experiment(0.1, 0.7), experiment(1.0, 0.6))

        assertEquals(0.1, bestExperiment(experiments).alpha)
    }

    @Test
    fun `最も高い実験が途中にあってもそれを選ぶ`() {
        val experiments = listOf(experiment(0.1, 0.5), experiment(1.0, 0.8), experiment(10.0, 0.6))

        assertEquals(1.0, bestExperiment(experiments).alpha)
    }
}

class ZeroCoefficientNamesTest {
    @Test
    fun `0になった係数の特徴量名を返す`() {
        assertEquals(listOf("RM", "RM^2"), zeroCoefficientNames(listOf(0.0, 1.5, 0.0), listOf("RM", "LSTAT", "RM^2")))
    }
}

internal fun randomDataset(): Pair<Matrix, List<Double>> {
    val random = Random(0)
    val weights = listOf(1.5, -2.0, 0.5, 3.0)
    val x = Matrix(List(30) { List(weights.size) { random.nextGaussian() } })
    val t = x.rows.map { row -> row.zip(weights).sumOf { (value, weight) -> value * weight } + 0.5 * random.nextGaussian() }
    return x to t
}

internal fun assertDoubles(
    expected: List<Double>,
    actual: List<Double>,
    tolerance: Double = 1e-9,
) {
    assertEquals(expected.size, actual.size, "要素の数")
    expected.zip(actual).forEachIndexed { i, (e, a) -> assertEquals(e, a, absoluteTolerance = tolerance, "${i}番目の要素") }
}
