package chapter07

import dataset.dataDir
import org.junit.jupiter.api.Assumptions.assumeTrue
import support.captureStdout
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CinemaDataTest {
    private val csvFile = File(dataDir(), "cinema.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ cinema.csv が配置されていない（gulp data:setup）")
    }

    @Test
    fun `実データから外れ値を1件取り除く`() {
        val df = loadCinema(csvFile)

        assertEquals(100 to 99, df.rowsCount() to removeOutliers(df).rowsCount())
    }

    @Test
    fun `実データで自作のモデルとTribuoのSLMTrainerのR2が一致する`() {
        val split = prepareCinema(csvFile, testSize = 0.2, seed = 0)

        val mine = fitLinearRegression(split.xTrain, split.tTrain).predict(split.xTest)
        val tribuo = predictWithTribuo(trainTribuoLinearRegression(split.xTrain, split.tTrain), split.xTest)

        assertEquals(r2Score(split.tTest, mine), r2Score(split.tTest, tribuo), absoluteTolerance = 1e-9)
    }

    @Test
    fun `実行すると学習した係数と評価指標を表示する`() {
        val output = captureStdout { main() }

        assertEquals(
            "データ件数: 100\n" +
                "外れ値を除いた件数: 99\n" +
                "訓練データ: 79 件, テストデータ: 20 件\n" +
                "切片: 6281.64\n" +
                "係数: SNS1=1.0947, SNS2=0.4886, actor=0.2831, original=236.3234\n" +
                "テストデータの評価: R2=0.8469, MAE=244.44, RMSE=285.37\n",
            output,
        )
    }
}
