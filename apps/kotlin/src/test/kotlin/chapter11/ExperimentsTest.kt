package chapter11

import chapter03.toTribuoDataset
import chapter03.trainTribuoTree
import dataset.dataDir
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.io.readCSV
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.tribuo.classification.Label
import org.tribuo.classification.evaluation.LabelEvaluator
import support.captureStdout
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

private fun assertScores(
    expected: Map<String, Double>,
    actual: Map<String, Double>,
    tolerance: Double,
) {
    assertEquals(expected.keys, actual.keys)
    for ((name, value) in expected) {
        assertEquals(value, actual.getValue(name), absoluteTolerance = tolerance, name)
    }
}

class SurvivedDataTest {
    private val csvFile = File(dataDir(), "Survived.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ Survived.csv が配置されていない（gulp data:setup）")
    }

    @Test
    fun `決定木を5分割交差検証で評価する`() {
        val scores = evaluateSurvived(csvFile)

        assertScores(mapOf("正解率" to 0.7677, "適合率" to 0.7716, "再現率" to 0.5900, "F値" to 0.6573), scores, tolerance = 1e-4)
    }

    @Test
    fun `同じ分割ならTribuoの評価器で採点した平均と一致する`() {
        val (x, t) = prepareSurvived(DataFrame.readCSV(csvFile))
        val folds = kFold(nSamples = x.rowsCount(), nSplits = N_SPLITS, seed = SEED)
        val positive = Label("1")

        val evaluations =
            folds.map { fold ->
                val model = trainTribuoTree(x[fold.train], t.slice(fold.train), maxDepth = 2, minChildWeight = 1.0f)
                LabelEvaluator().evaluate(model, toTribuoDataset(x[fold.test], t.slice(fold.test)))
            }

        val scores = evaluate({ TribuoTree(maxDepth = 2) }, x, t, SURVIVED_METRICS)
        val tribuo =
            mapOf(
                "正解率" to evaluations.map { it.accuracy() }.average(),
                "適合率" to evaluations.map { it.precision(positive) }.average(),
                "再現率" to evaluations.map { it.recall(positive) }.average(),
                "F値" to evaluations.map { it.f1(positive) }.average(),
            )
        assertScores(tribuo, scores, tolerance = 1e-12)
    }
}

class CinemaDataTest {
    private val csvFile = File(dataDir(), "cinema.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ cinema.csv が配置されていない（gulp data:setup）")
    }

    @Test
    fun `線形回帰を5分割交差検証で評価する`() {
        val scores = evaluateCinema(csvFile)

        assertScores(mapOf("RMSE" to 406.75, "MAE" to 327.68), scores, tolerance = 1e-2)
    }
}

class MainTest {
    @BeforeTest
    fun requireData() {
        assumeTrue(
            File(dataDir(), "Survived.csv").exists() && File(dataDir(), "cinema.csv").exists(),
            "学習データ Survived.csv・cinema.csv が配置されていない（gulp data:setup）",
        )
    }

    @Test
    fun `実行すると交差検証の平均を表示する`() {
        val output = captureStdout { main() }

        assertEquals(
            "Survived（決定木、5 分割交差検証の平均）\n" +
                "  正解率: 0.7677\n" +
                "  適合率: 0.7716\n" +
                "  再現率: 0.5900\n" +
                "  F値: 0.6573\n" +
                "cinema（線形回帰、5 分割交差検証の平均）\n" +
                "  RMSE: 406.75\n" +
                "  MAE: 327.68\n",
            output,
        )
    }
}
