package chapter10

import chapter02.TrainTestSplit
import chapter02.prepareIris
import dataset.dataDir
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.tribuo.Trainer
import org.tribuo.classification.dtree.CARTClassificationTrainer
import org.tribuo.classification.dtree.impurity.GiniIndex
import org.tribuo.classification.ensemble.VotingCombiner
import org.tribuo.classification.sgd.linear.LinearSGDTrainer
import org.tribuo.classification.sgd.linear.LogisticRegressionTrainer
import org.tribuo.classification.sgd.objectives.LogMulticlass
import org.tribuo.common.tree.RandomForestTrainer
import org.tribuo.math.optimisers.AdaGrad
import support.captureStdout
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IrisModelsTest {
    private val csvFile = File(dataDir(), "iris.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ iris.csv が配置されていない（gulp data:setup）")
    }

    private fun irisSplit(): TrainTestSplit<String> = prepareIris(csvFile, testSize = 0.3, seed = 0)

    @Test
    fun `ロジスティック回帰はテストデータの45件中42件を正しく分類する`() {
        val score = evaluate(LogisticRegression(), irisSplit())

        assertEquals(42.0 / 45, score.test, absoluteTolerance = 1e-12)
    }

    @Test
    fun `ランダムフォレストは訓練データを分け切りテストデータの45件中41件を正しく分類する`() {
        val score = evaluate(RandomForest(nEstimators = 100, maxFeatures = 2, seed = 0), irisSplit())

        assertEquals(1.0, score.train)
        assertEquals(41.0 / 45, score.test, absoluteTolerance = 1e-12)
    }

    @Test
    fun `Tribuoの既定のロジスティック回帰は5エポックでテストデータの45件中41件を正しく分類する`() {
        val score = evaluate(TribuoClassifier(LogisticRegressionTrainer()), irisSplit())

        assertEquals(84.0 / 105, score.train, absoluteTolerance = 1e-12)
        assertEquals(41.0 / 45, score.test, absoluteTolerance = 1e-12)
    }

    @Test
    fun `Tribuoのロジスティック回帰は500エポックで自作と同じ正解率になる`() {
        val trainer = LinearSGDTrainer(LogMulticlass(), AdaGrad(1.0, 0.1), 500, Trainer.DEFAULT_SEED)

        val tribuo = evaluate(TribuoClassifier(trainer), irisSplit())

        assertEquals(evaluate(LogisticRegression(), irisSplit()), tribuo)
    }

    @Test
    fun `Tribuoのランダムフォレストはテストデータの45件中41件を正しく分類する`() {
        val score = evaluate(tribuoRandomForest(nEstimators = 100, maxDepth = null, seed = 0L), irisSplit())

        assertEquals(41.0 / 45, score.test, absoluteTolerance = 1e-12)
    }

    @Test
    fun `Tribuoのランダムフォレストは最小の重みを1にすると訓練データを分け切る`() {
        val tree = CARTClassificationTrainer(Int.MAX_VALUE, 1.0f, 0.0f, 0.5f, GiniIndex(), 0L)
        val forest = TribuoClassifier(RandomForestTrainer(tree, VotingCombiner(), 100, 0L))

        val score = evaluate(forest, irisSplit())

        assertEquals(1.0, score.train)
        assertEquals(43.0 / 45, score.test, absoluteTolerance = 1e-12)
    }

    @Test
    fun `実行するとモデルごとの正解率とランダムフォレストの重要度を表示する`() {
        val output = captureStdout { main() }

        assertEquals(
            "モデル\t訓練データ\tテストデータ\n" +
                "決定木（深さ 2）\t0.9429\t0.9333\n" +
                "ロジスティック回帰\t0.9429\t0.9333\n" +
                "ランダムフォレスト（100 本）\t1.0000\t0.9111\n" +
                "ランダムフォレスト（100 本・深さ 2）\t0.9429\t0.9333\n" +
                "Tribuo ロジスティック回帰\t0.8000\t0.9111\n" +
                "Tribuo ランダムフォレスト（100 本）\t0.9810\t0.9111\n" +
                "\n" +
                "ランダムフォレスト（100 本）の特徴量の重要度:\n" +
                "がく片長さ\t0.2116\n" +
                "がく片幅\t0.1215\n" +
                "花弁長さ\t0.2695\n" +
                "花弁幅\t0.3973\n",
            output,
        )
    }
}
