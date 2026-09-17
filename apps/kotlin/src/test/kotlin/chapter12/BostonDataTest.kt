package chapter12

import dataset.dataDir
import org.junit.jupiter.api.Assumptions.assumeTrue
import support.captureStdout
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BostonDataTest {
    private val csvFile = File(dataDir(), "Boston.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ Boston.csv が配置されていない（gulp data:setup）")
    }

    private fun dataset(): BostonDataset = prepareBoston(csvFile, testSize = 0.3, validationSize = 0.3, seed = 0)

    @Test
    fun `外れ値を除いて訓練データと検証データとテストデータに分ける`() {
        val dataset = dataset()

        assertEquals(listOf(47, 21, 30), listOf(dataset.tTrain, dataset.tValid, dataset.tTest).map { it.size })
    }

    @Test
    fun `実データでも自作のリッジ回帰はTribuoのElasticNetCDTrainerと同じ係数と切片になる`() {
        val dataset = dataset()

        val model = fitRidge(dataset.xTrain, dataset.tTrain, alpha = 10.0)
        val tribuo = fitRidgeWithTribuo(dataset.xTrain, dataset.tTrain, alpha = 10.0)

        assertDoubles(model.coefficients, tribuo.coefficients, tolerance = 1e-6)
        assertEquals(model.intercept, tribuo.intercept, absoluteTolerance = 1e-6)
    }

    @Test
    fun `実行すると正則化の実験結果を表示する`() {
        val output = captureStdout { main() }

        assertEquals(
            "データ件数: 98（外れ値 2 件を除外）\n" +
                "訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件\n" +
                "特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2\n" +
                "alpha  訓練 R²  検証 R²  係数の絶対値の合計\n" +
                "  0.0  0.9044  0.1214  13.981\n" +
                "  0.1  0.9044  0.1371  13.895\n" +
                "  1.0  0.9039  0.2367  13.271\n" +
                " 10.0  0.8931  0.4271  10.926\n" +
                "100.0  0.7650  0.4191  6.971\n" +
                "検証データで選んだ alpha: 10.0\n" +
                "テストデータの決定係数: 線形回帰 0.8123, リッジ回帰 0.7498\n" +
                "ラッソ回帰（alpha=0.5）で係数が 0 になった特徴量: RM LSTAT, PTRATIO^2, PTRATIO LSTAT\n",
            output,
        )
    }
}
