package chapter13

import chapter07.Matrix
import chapter07.toMatrix
import dataset.dataDir
import org.junit.jupiter.api.Assumptions.assumeTrue
import support.captureStdout
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class BostonPcaDataTest {
    private val csvFile = File(dataDir(), "Boston.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ Boston.csv が配置されていない（gulp data:setup）")
    }

    @Test
    fun `CRIMEをダミー変数にして15列の標準化済みデータにする`() {
        val df = loadStandardizedBoston(csvFile)

        assertEquals(100 to 15, df.rowsCount() to df.columnsCount())
    }

    @Test
    fun `実データの主成分も分散共分散行列の固有ベクトルになる`() {
        val df = loadStandardizedBoston(csvFile)
        val x = df.toMatrix(df.columnNames())

        val model = fitPca(x, nComponents = 15)

        val covariance = covarianceMatrix(x)
        model.components.rows.zip(model.explainedVariance).forEach { (component, variance) ->
            val projected = covariance * Matrix(component.map { listOf(it) })
            component.zip(projected.columns.first()).forEach { (v, av) -> assertEquals(v * variance, av, absoluteTolerance = 1e-9) }
        }
        assertEquals(1.0, model.explainedVarianceRatio.sum(), absoluteTolerance = 1e-9)
    }

    @Test
    fun `実行すると寄与率と主成分の解釈を表示する`() {
        val output = captureStdout { main() }

        assertEquals(
            "データ件数: 100, 列数: 15\n" +
                "寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581\n" +
                "累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）\n" +
                "第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328\n" +
                "第 2 主成分で影響の大きい列: PRICE 0.444, low 0.423, RM 0.405\n",
            output,
        )
    }
}
