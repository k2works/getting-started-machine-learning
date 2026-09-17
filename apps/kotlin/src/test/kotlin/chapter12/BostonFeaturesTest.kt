package chapter12

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class PolynomialScalerTest {
    @Test
    fun `訓練データで標準化してから2乗の列を加える`() {
        val x = dataFrameOf("RM" to listOf(1.0, 2.0, 3.0))

        val scaler = fitPolynomialScaler(x)

        val z = 1.224744871391589
        val rows = scaler.transform(x).rows
        assertDoubles(listOf(-z, z * z), rows[0])
        assertDoubles(listOf(0.0, 0.0), rows[1])
        assertDoubles(listOf(z, z * z), rows[2])
    }

    @Test
    fun `テストデータも訓練データの平均値と標準偏差で標準化する`() {
        val train = dataFrameOf("RM" to listOf(1.0, 2.0, 3.0))
        val test = dataFrameOf("RM" to listOf(2.0))

        val scaler = fitPolynomialScaler(train)

        assertDoubles(listOf(0.0, 0.0), scaler.transform(test).rows.single())
    }

    @Test
    fun `2つの特徴量から2乗と交互作用の列を作り名前を付ける`() {
        val x =
            dataFrameOf(
                "RM" to listOf(1.0, 2.0, 3.0),
                "LSTAT" to listOf(3.0, 1.0, 2.0),
            )

        val scaler = fitPolynomialScaler(x)

        assertEquals(listOf("RM", "LSTAT", "RM^2", "RM LSTAT", "LSTAT^2"), scaler.featureNames)
        assertEquals(3 to 5, scaler.transform(x).let { it.rows.size to it.columns.size })
    }
}

class RemoveOutliersTest {
    @Test
    fun `平均から標準偏差の3倍より離れた値を持つ行を除く`() {
        val df = dataFrameOf("RM" to List(11) { 1.0 } + 100.0)

        assertEquals(List(11) { 1.0 }, removeOutliers(df, listOf("RM"), threshold = 3.0)["RM"].toList())
    }

    @Test
    fun `外れ値が無ければすべての行を残す`() {
        val df = dataFrameOf("RM" to listOf(5.0, 6.0, 7.0))

        assertEquals(3, removeOutliers(df, listOf("RM"), threshold = 3.0).rowsCount())
    }

    @Test
    fun `指定した列の値だけで外れ値を判定する`() {
        val df =
            dataFrameOf(
                "RM" to List(12) { 1.0 },
                "ZN" to List(11) { 0.0 } + 100.0,
            )

        assertEquals(12, removeOutliers(df, listOf("RM"), threshold = 3.0).rowsCount())
    }
}

class PrepareBostonTest {
    private val directory: Path = createTempDirectory()

    @Test
    fun `訓練データと検証データとテストデータに分けて多項式特徴量を作る`() {
        val rows = (0 until 10).joinToString("\n") { i -> "low,6.$i,1$i.5,${i + 3}.2,2$i.0" }
        val csvFile = File(directory.toFile(), "boston.csv").apply { writeText("CRIME,RM,PTRATIO,LSTAT,PRICE\n$rows\n") }

        val dataset = prepareBoston(csvFile, testSize = 0.3, validationSize = 0.3, seed = 0)

        val shapes = listOf(dataset.xTrain, dataset.xValid, dataset.xTest).map { it.rows.size to it.columns.size }
        assertEquals(listOf(4 to 9, 3 to 9, 3 to 9), shapes)
        assertEquals(listOf(4, 3, 3), listOf(dataset.tTrain, dataset.tValid, dataset.tTest).map { it.size })
        assertEquals(listOf("RM", "PTRATIO", "LSTAT"), dataset.featureNames.take(3))
    }
}
