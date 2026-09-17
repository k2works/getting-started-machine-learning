package chapter07

import chapter02.countMissing
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

private const val HEADER = "cinema_id,SNS1,SNS2,actor,original,sales\n"

class LoadCinemaTest {
    private val directory: Path = createTempDirectory()

    @Test
    fun `CSVを読み込み空欄を欠損値にする`() {
        val csvFile = File(directory.toFile(), "cinema.csv").apply { writeText(HEADER + "1,,500,9000.5,1,9500\n") }

        val df = loadCinema(csvFile)

        assertEquals(listOf("cinema_id", "SNS1", "SNS2", "actor", "original", "sales"), df.columnNames())
        assertNull(df["SNS1"][0])
    }
}

class PrepareCinemaTest {
    private val directory: Path = createTempDirectory()

    @Test
    fun `外れ値を除き特徴量を選んで分割し欠損値を補完する`() {
        val csvFile =
            File(directory.toFile(), "cinema.csv").apply {
                writeText(
                    HEADER +
                        "1,100,300,9000.0,0,9200\n" +
                        "2,,400,9500.0,1,9800\n" +
                        "3,300,500,,1,10100\n" +
                        "4,150,1200,8800.0,0,8100\n" +
                        "5,250,700,9900.0,1,10300\n" +
                        "6,120,650,9100.0,0,9400\n",
                )
            }

        val split = prepareCinema(csvFile, testSize = 0.4, seed = 0)

        assertEquals(listOf("SNS1", "SNS2", "actor", "original"), split.xTrain.columnNames())
        assertEquals(3 to 2, split.xTrain.rowsCount() to split.xTest.rowsCount())
        assertFalse(8100.0 in split.tTrain + split.tTest)
        assertEquals(0, countMissing(split.xTrain).values.sum() + countMissing(split.xTest).values.sum())
    }
}

class RemoveOutliersTest {
    @Test
    fun `SNS2が1000を超え売上が8500未満の行を取り除く`() {
        val df = dataFrameOf("SNS2" to listOf(1200, 600), "sales" to listOf(8000, 9500))

        assertEquals(listOf(600), removeOutliers(df)["SNS2"].toList())
    }

    @Test
    fun `条件の片方だけを満たす行は残す`() {
        val df = dataFrameOf("SNS2" to listOf(1200, 600), "sales" to listOf(9800, 8000))

        assertEquals(listOf(1200, 600), removeOutliers(df)["SNS2"].toList())
    }
}

private fun assertModel(
    intercept: Double,
    coefficients: Map<String, Double>,
    actual: LinearModel,
) {
    assertEquals(intercept, actual.intercept, absoluteTolerance = 1e-9)
    assertEquals(coefficients.keys.toList(), actual.coefficients.keys.toList())
    coefficients.forEach { (name, value) -> assertEquals(value, actual.coefficients.getValue(name), absoluteTolerance = 1e-9) }
}

class FitLinearRegressionTest {
    @Test
    fun `直線上の点から切片と係数を求める`() {
        val x = dataFrameOf("x" to listOf(0.0, 1.0, 2.0, 3.0))
        val t = listOf(1.0, 3.0, 5.0, 7.0)

        val model = fitLinearRegression(x, t)

        assertModel(intercept = 1.0, coefficients = mapOf("x" to 2.0), actual = model)
    }

    @Test
    fun `複数の特徴量から切片と係数を求める`() {
        val a = listOf(0.0, 1.0, 0.0, 2.0, 1.0)
        val b = listOf(0.0, 0.0, 1.0, 1.0, 3.0)
        val x = dataFrameOf("a" to a, "b" to b)
        val t = a.zip(b) { ai, bi -> 3.0 * ai - 2.0 * bi + 5.0 }

        val model = fitLinearRegression(x, t)

        assertModel(intercept = 5.0, coefficients = mapOf("a" to 3.0, "b" to -2.0), actual = model)
    }
}

internal fun assertDoubles(
    expected: List<Double>,
    actual: List<Double>,
) {
    assertEquals(expected.size, actual.size)
    expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, absoluteTolerance = 1e-9) }
}

class PredictTest {
    private val model = LinearModel(intercept = 1.0, coefficients = mapOf("a" to 2.0, "b" to -1.0))

    @Test
    fun `切片と係数から予測値を計算する`() {
        val x = dataFrameOf("a" to listOf(1.0, 3.0), "b" to listOf(4.0, 0.5))

        assertDoubles(listOf(-1.0, 6.5), model.predict(x))
    }

    @Test
    fun `列の並び順が違っても列名で係数を対応させる`() {
        val x = dataFrameOf("b" to listOf(4.0, 0.5), "a" to listOf(1.0, 3.0))

        assertDoubles(listOf(-1.0, 6.5), model.predict(x))
    }
}
