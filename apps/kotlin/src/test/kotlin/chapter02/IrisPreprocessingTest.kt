package chapter02

import dataset.dataDir
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.mean
import org.junit.jupiter.api.Assumptions.assumeTrue
import support.captureStdout
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

private const val HEADER = "\uFEFFがく片長さ,がく片幅,花弁長さ,花弁幅,種類\n"

class LoadIrisTest {
    private val directory: Path = createTempDirectory()

    private fun writeCsv(rows: String): File = File(directory.toFile(), "iris.csv").apply { writeText(HEADER + rows) }

    @Test
    fun `BOM付きCSVを読み込むと列名にBOMが残らない`() {
        val df = loadIris(writeCsv("0.1,0.2,0.3,0.4,Iris-setosa\n"))

        assertEquals(listOf("がく片長さ", "がく片幅", "花弁長さ", "花弁幅", "種類"), df.columnNames())
    }

    @Test
    fun `空欄は欠損値のnullとして読み込む`() {
        val df = loadIris(writeCsv("0.1,,0.3,0.4,Iris-setosa\n"))

        assertNull(df["がく片幅"][0])
    }
}

class CountMissingTest {
    @Test
    fun `列ごとの欠損値の数を数える`() {
        val df =
            dataFrameOf("がく片長さ", "がく片幅", "種類")(
                0.1,
                0.2,
                "Iris-setosa",
                null,
                0.3,
                "Iris-setosa",
                null,
                null,
                "Iris-virginica",
            )

        assertEquals(mapOf("がく片長さ" to 2, "がく片幅" to 1, "種類" to 0), countMissing(df))
    }
}

class ColumnMeansTest {
    @Test
    fun `欠損値を除いて列ごとの平均値を求める`() {
        val df =
            dataFrameOf("がく片長さ", "がく片幅")(
                0.1,
                0.2,
                null,
                0.4,
                0.3,
                0.9,
            )

        val means = columnMeans(df, listOf("がく片長さ", "がく片幅"))

        assertEquals(0.2, means.getValue("がく片長さ"), absoluteTolerance = 1e-12)
        assertEquals(0.5, means.getValue("がく片幅"), absoluteTolerance = 1e-12)
    }
}

class FillMissingTest {
    @Test
    fun `欠損値を列ごとに指定した値で補完する`() {
        val df =
            dataFrameOf("がく片長さ", "がく片幅")(
                0.1,
                null,
                null,
                0.4,
            )

        val filled = fillMissing(df, mapOf("がく片長さ" to 0.2, "がく片幅" to 0.5))

        assertEquals(listOf(0.1, 0.2), filled["がく片長さ"].toList())
        assertEquals(listOf(0.5, 0.4), filled["がく片幅"].toList())
    }

    @Test
    fun `元のデータフレームは変更しない`() {
        val df = dataFrameOf("がく片長さ")(0.1, null)

        fillMissing(df, mapOf("がく片長さ" to 0.2))

        assertEquals(1, countMissing(df).getValue("がく片長さ"))
    }
}

class SplitFeaturesAndTargetTest {
    @Test
    fun `特徴量の列と正解ラベルの列に分ける`() {
        val df =
            dataFrameOf("がく片長さ", "花弁幅", "種類")(
                0.1,
                0.4,
                "Iris-setosa",
                0.5,
                0.8,
                "Iris-virginica",
            )

        val (x, t) = splitFeaturesAndTarget(df, "種類")

        assertEquals(listOf("がく片長さ", "花弁幅"), x.columnNames())
        assertEquals(listOf("Iris-setosa", "Iris-virginica"), t)
    }
}

private fun numberedDataset(size: Int): Pair<AnyFrame, List<String>> {
    val x = dataFrameOf("x")(*(0 until size).toList().toTypedArray())
    val t = (0 until size).map { "label$it" }
    return x to t
}

class SplitTrainTestTest {
    @Test
    fun `テストデータの割合どおりの件数に分ける`() {
        val (x, t) = numberedDataset(10)

        val split = splitTrainTest(x, t, testSize = 0.3, seed = 0)

        assertEquals(7 to 3, split.xTrain.rowsCount() to split.xTest.rowsCount())
        assertEquals(7 to 3, split.tTrain.size to split.tTest.size)
    }

    @Test
    fun `件数が変わってもテストデータの割合どおりに分ける`() {
        val (x, t) = numberedDataset(20)

        val split = splitTrainTest(x, t, testSize = 0.25, seed = 0)

        assertEquals(15 to 5, split.xTrain.rowsCount() to split.xTest.rowsCount())
        assertEquals(15 to 5, split.tTrain.size to split.tTest.size)
    }

    @Test
    fun `すべての行を重複なく訓練データとテストデータのどちらかに入れる`() {
        val (x, t) = numberedDataset(10)

        val split = splitTrainTest(x, t, testSize = 0.3, seed = 0)

        val trainRows = split.xTrain["x"].values().toSet()
        val testRows = split.xTest["x"].values().toSet()
        assertEquals((0 until 10).toSet(), trainRows + testRows)
        assertEquals(emptySet(), trainRows intersect testRows)
    }

    @Test
    fun `特徴量と正解ラベルの対応を保ったまま分ける`() {
        val (x, t) = numberedDataset(10)

        val split = splitTrainTest(x, t, testSize = 0.3, seed = 0)

        assertEquals(split.xTrain["x"].values().map { "label$it" }, split.tTrain)
        assertEquals(split.xTest["x"].values().map { "label$it" }, split.tTest)
    }

    @Test
    fun `同じシードなら同じ分け方になる`() {
        val (x, t) = numberedDataset(10)

        val first = splitTrainTest(x, t, testSize = 0.3, seed = 42)
        val second = splitTrainTest(x, t, testSize = 0.3, seed = 42)

        assertEquals(first.tTest, second.tTest)
    }

    @Test
    fun `シードが違えば違う分け方になる`() {
        val (x, t) = numberedDataset(10)

        val first = splitTrainTest(x, t, testSize = 0.3, seed = 0)
        val second = splitTrainTest(x, t, testSize = 0.3, seed = 1)

        assertNotEquals(first.tTest, second.tTest)
    }
}

class DataFrameMeanLearningTest {
    @Test
    fun `DataFrameのmeanも欠損値を除いて平均値を求める`() {
        val df = dataFrameOf("がく片長さ")(0.1, null, 0.3)

        assertEquals(columnMeans(df, listOf("がく片長さ")).getValue("がく片長さ"), df["がく片長さ"].cast<Double?>().mean(), absoluteTolerance = 1e-12)
    }
}

class PrepareIrisTest {
    private val directory: Path = createTempDirectory()

    @Test
    fun `訓練データとテストデータのどちらにも欠損値が残らない`() {
        val csvFile =
            File(directory.toFile(), "iris.csv").apply {
                writeText(
                    HEADER +
                        "0.1,,0.3,0.4,Iris-setosa\n" +
                        "0.2,0.3,,0.5,Iris-setosa\n" +
                        ",0.4,0.5,0.6,Iris-virginica\n" +
                        "0.4,0.5,0.6,,Iris-virginica\n",
                )
            }

        val split = prepareIris(csvFile, testSize = 0.5, seed = 0)

        assertEquals(0, countMissing(split.xTrain).values.sum())
        assertEquals(0, countMissing(split.xTest).values.sum())
    }
}

class IrisDataTest {
    private val csvFile = File(dataDir(), "iris.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ iris.csv が配置されていない（gulp data:setup）")
    }

    @Test
    fun `実データの列ごとの欠損値の数を数える`() {
        assertEquals(
            mapOf("がく片長さ" to 2, "がく片幅" to 1, "花弁長さ" to 2, "花弁幅" to 2, "種類" to 0),
            countMissing(loadIris(csvFile)),
        )
    }

    @Test
    fun `実データを105件と45件に分けて欠損値を補完する`() {
        val split = prepareIris(csvFile, testSize = 0.3, seed = 0)

        assertEquals(105 to 45, split.xTrain.rowsCount() to split.xTest.rowsCount())
        assertEquals(0, countMissing(split.xTrain).values.sum())
        assertEquals(0, countMissing(split.xTest).values.sum())
    }

    @Test
    fun `実行すると前処理の結果を表示する`() {
        val output = captureStdout { main() }

        assertEquals(
            "データ件数: 150\n" +
                "欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0\n" +
                "訓練データ: 105 件, テストデータ: 45 件\n" +
                "補完後の欠損値の数: 訓練データ 0, テストデータ 0\n",
            output,
        )
    }
}
