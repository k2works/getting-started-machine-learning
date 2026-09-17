package chapter03

import chapter01.accuracy
import chapter02.TrainTestSplit
import chapter02.prepareIris
import dataset.dataDir
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.junit.jupiter.api.Assumptions.assumeTrue
import support.captureStdout
import java.io.File
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GiniTest {
    @Test
    fun `1種類のラベルだけならジニ不純度は0`() {
        assertEquals(0.0, gini(listOf("Iris-setosa", "Iris-setosa", "Iris-setosa")))
    }

    @Test
    fun `2種類のラベルが半分ずつならジニ不純度は05`() {
        assertEquals(0.5, gini(listOf("Iris-setosa", "Iris-virginica")))
    }

    @Test
    fun `3種類のラベルが同じ数ならジニ不純度は3分の2`() {
        val labels = listOf("Iris-setosa", "Iris-versicolor", "Iris-virginica")

        assertEquals(2.0 / 3, gini(labels), absoluteTolerance = 1e-12)
    }
}

private fun assertSplit(
    feature: String,
    threshold: Double,
    impurity: Double,
    actual: Split?,
) {
    val split = assertNotNull(actual)
    assertEquals(feature, split.feature)
    assertEquals(threshold, split.threshold, absoluteTolerance = 1e-12)
    assertEquals(impurity, split.impurity, absoluteTolerance = 1e-12)
}

class BestSplitTest {
    @Test
    fun `ラベルを完全に分けられる境界を見つける`() {
        val x = dataFrameOf("花弁幅" to listOf(0.1, 0.2, 0.7, 0.8))
        val t = listOf("setosa", "setosa", "virginica", "virginica")

        assertSplit(feature = "花弁幅", threshold = 0.45, impurity = 0.0, actual = bestSplit(x, t))
    }

    @Test
    fun `複数の特徴量から不純度が最も小さくなる特徴量と境界を選ぶ`() {
        val x =
            dataFrameOf(
                "がく片長さ" to listOf(0.1, 0.3, 0.2, 0.4),
                "花弁長さ" to listOf(0.2, 0.1, 0.9, 0.6),
            )
        val t = listOf("setosa", "setosa", "virginica", "virginica")

        assertSplit(feature = "花弁長さ", threshold = 0.4, impurity = 0.0, actual = bestSplit(x, t))
    }

    @Test
    fun `ラベルが1種類なら分割しない`() {
        val x = dataFrameOf("花弁幅" to listOf(0.1, 0.2, 0.7))
        val t = listOf("setosa", "setosa", "setosa")

        assertNull(bestSplit(x, t))
    }
}

class DecisionTreeTest {
    @Test
    fun `1種類のラベルだけを学習するとそのラベルを予測する`() {
        val x = dataFrameOf("花弁幅" to listOf(0.1, 0.2))
        val t = listOf("setosa", "setosa")

        val model = DecisionTree().fit(x, t)

        assertEquals(listOf("setosa", "setosa"), model.predict(dataFrameOf("花弁幅" to listOf(0.15, 0.9))))
    }

    @Test
    fun `境界の左右で異なるラベルを予測する`() {
        val x = dataFrameOf("花弁幅" to listOf(0.1, 0.2, 0.7, 0.8))
        val t = listOf("setosa", "setosa", "virginica", "virginica")

        val model = DecisionTree().fit(x, t)

        assertEquals(listOf("setosa", "virginica"), model.predict(dataFrameOf("花弁幅" to listOf(0.15, 0.75))))
    }
}

internal fun threeSpecies(): Pair<AnyFrame, List<String>> {
    val x = dataFrameOf("花弁幅" to listOf(0.1, 0.2, 0.3, 0.5, 0.6, 0.9))
    val t = listOf("setosa", "setosa", "setosa", "versicolor", "versicolor", "virginica")
    return x to t
}

class MaxDepthTest {
    @Test
    fun `深さを制限しなければすべての訓練データを分け切る`() {
        val (x, t) = threeSpecies()

        val model = DecisionTree().fit(x, t)

        assertEquals(t, model.predict(x))
    }

    @Test
    fun `深さを1に制限すると境界の先は多数派のラベルを予測する`() {
        val (x, t) = threeSpecies()

        val model = DecisionTree(maxDepth = 1).fit(x, t)

        assertEquals(listOf("setosa", "versicolor"), model.predict(dataFrameOf("花弁幅" to listOf(0.2, 0.95))))
    }

    @Test
    fun `学習する前に予測するとエラーになる`() {
        val error = assertFailsWith<IllegalStateException> { DecisionTree().predict(dataFrameOf("花弁幅" to listOf(0.1))) }

        assertEquals("fit で学習してから predict を呼んでください", error.message)
    }
}

class FormatTreeTest {
    @Test
    fun `葉だけの木はラベルを表示する`() {
        assertEquals("setosa", formatTree(Leaf(label = "setosa")))
    }

    @Test
    fun `節は条件ごとに字下げして表示する`() {
        val tree =
            Node(
                split = Split(feature = "花弁幅", threshold = 0.4, impurity = 0.0),
                left = Leaf(label = "setosa"),
                right =
                    Node(
                        split = Split(feature = "花弁長さ", threshold = 0.75, impurity = 0.0),
                        left = Leaf(label = "versicolor"),
                        right = Leaf(label = "virginica"),
                    ),
            )

        assertEquals(
            """
            花弁幅 <= 0.4000
              setosa
            花弁幅 > 0.4000
              花弁長さ <= 0.7500
                versicolor
              花弁長さ > 0.7500
                virginica
            """.trimIndent(),
            formatTree(tree),
        )
    }
}

class IrisDataTest {
    private val csvFile = File(dataDir(), "iris.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ iris.csv が配置されていない（gulp data:setup）")
    }

    private fun irisSplit(): TrainTestSplit<String> = prepareIris(csvFile, testSize = 0.3, seed = 0)

    private fun countDifferences(
        split: TrainTestSplit<String>,
        maxDepth: Int?,
    ): Int {
        val mine = DecisionTree(maxDepth).fit(split.xTrain, split.tTrain).predict(split.xTest)
        val tribuo = predictWithTribuo(trainTribuoTree(split.xTrain, split.tTrain, maxDepth, 1.0f), split.xTest)
        return mine.zip(tribuo).count { (a, b) -> a != b }
    }

    @Test
    fun `深さ2の決定木はテストデータの45件中42件を正しく分類する`() {
        val split = irisSplit()

        val predictions = DecisionTree(maxDepth = 2).fit(split.xTrain, split.tTrain).predict(split.xTest)

        assertEquals(42.0 / 45, accuracy(predictions, split.tTest), absoluteTolerance = 1e-12)
    }

    @Test
    fun `深さ2までならTribuoのCARTとテストデータの予測が一致する`() {
        val split = irisSplit()

        for (maxDepth in listOf(1, 2)) {
            assertEquals(0, countDifferences(split, maxDepth), "深さ $maxDepth")
        }
    }

    @Test
    fun `深さ3では多数決が同数の葉に落ちる1件だけTribuoと予測が違う`() {
        assertEquals(1, countDifferences(irisSplit(), maxDepth = 3))
    }

    @Test
    fun `深さ4以上でも1件だけTribuoと予測が違う`() {
        val split = irisSplit()

        for (maxDepth in listOf(4, 5, null)) {
            assertEquals(1, countDifferences(split, maxDepth), "深さ $maxDepth")
        }
    }

    @Test
    fun `実行すると深さごとの正解率と深さ2の決定木を表示する`() {
        val output = captureStdout { main() }

        assertEquals(
            "深さ\t訓練データ\tテストデータ\n" +
                "1\t0.6762\t0.6444\n" +
                "2\t0.9429\t0.9333\n" +
                "3\t0.9619\t0.9333\n" +
                "4\t0.9810\t0.8889\n" +
                "5\t0.9810\t0.8889\n" +
                "制限なし\t1.0000\t0.8444\n" +
                "\n" +
                "深さ 2 の決定木:\n" +
                "花弁幅 <= 0.2750\n" +
                "  Iris-setosa\n" +
                "花弁幅 > 0.2750\n" +
                "  花弁幅 <= 0.6900\n" +
                "    Iris-versicolor\n" +
                "  花弁幅 > 0.6900\n" +
                "    Iris-virginica\n",
            output,
        )
    }
}
