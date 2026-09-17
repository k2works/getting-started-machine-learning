package chapter14

import dataset.dataDir
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.junit.jupiter.api.Assumptions.assumeTrue
import support.captureStdout
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.math.sqrt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val HEADER = "Channel,Region,Fresh,Milk,Grocery,Frozen,Detergents_Paper,Delicassen\n"

class LoadSpendingTest {
    private val directory: Path = createTempDirectory()

    private fun writeCsv(rows: String): File = File(directory.toFile(), "wholesale.csv").apply { writeText(HEADER + rows) }

    @Test
    fun `ChannelとRegionを除いた支出額の列を読み込む`() {
        val df = loadSpending(writeCsv("1,2,100,200,300,400,500,600\n"))

        assertEquals(listOf("Fresh", "Milk", "Grocery", "Frozen", "Detergents_Paper", "Delicassen"), df.columnNames())
        assertEquals(listOf(100, 200, 300, 400, 500, 600), df.columnNames().map { df[it][0] })
    }
}

class StandardizeTest {
    @Test
    fun `列ごとに平均0標準偏差1の点のリストに変換する`() {
        val df = dataFrameOf("Fresh" to listOf(10.0, 20.0, 30.0), "Milk" to listOf(5.0, 5.0, 8.0))

        val points = standardize(df)

        for (column in 0..1) {
            val values = points.map { it[column] }
            val mean = values.average()
            assertEquals(0.0, mean, absoluteTolerance = 1e-12)
            assertEquals(1.0, sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size), absoluteTolerance = 1e-12)
        }
    }
}

class AssignClustersTest {
    @Test
    fun `各点を最も近い中心のクラスタに割り当てる`() {
        val points = listOf(listOf(0.0), listOf(1.0), listOf(9.0), listOf(10.0))
        val centers = listOf(listOf(0.0), listOf(10.0))

        assertEquals(listOf(0, 0, 1, 1), assignClusters(points, centers))
    }

    @Test
    fun `2次元の点をユークリッド距離で最も近い中心に割り当てる`() {
        val points = listOf(listOf(0.0, 0.0), listOf(5.0, 4.0), listOf(1.0, 0.0))
        val centers = listOf(listOf(5.0, 5.0), listOf(0.0, 0.0))

        assertEquals(listOf(1, 0, 1), assignClusters(points, centers))
    }
}

class UpdateCentersTest {
    @Test
    fun `クラスタごとに割り当てられた点の平均を新しい中心にする`() {
        val points = listOf(listOf(0.0, 0.0), listOf(2.0, 0.0), listOf(10.0, 10.0), listOf(10.0, 12.0))
        val previous = listOf(listOf(0.0, 0.0), listOf(0.0, 0.0))

        assertEquals(listOf(listOf(1.0, 0.0), listOf(10.0, 11.0)), updateCenters(points, listOf(0, 0, 1, 1), previous))
    }

    @Test
    fun `点が1つも割り当てられなかったクラスタは中心を変えない`() {
        val points = listOf(listOf(0.0, 0.0), listOf(2.0, 4.0))
        val previous = listOf(listOf(0.0, 0.0), listOf(99.0, 99.0))

        assertEquals(listOf(listOf(1.0, 2.0), listOf(99.0, 99.0)), updateCenters(points, listOf(0, 0), previous))
    }
}

class SumOfSquaredErrorsTest {
    @Test
    fun `各点と所属するクラスタの中心との距離の2乗を合計する`() {
        val points = listOf(listOf(0.0, 0.0), listOf(2.0, 0.0), listOf(10.0, 10.0), listOf(10.0, 12.0))
        val centers = listOf(listOf(1.0, 0.0), listOf(10.0, 11.0))

        assertEquals(4.0, sumOfSquaredErrors(points, listOf(0, 0, 1, 1), centers))
    }

    @Test
    fun `中心から離れた点ほど誤差が大きくなる`() {
        assertEquals(10.0, sumOfSquaredErrors(listOf(listOf(0.0), listOf(4.0)), listOf(0, 0), listOf(listOf(1.0))))
    }
}

private fun assertPointsEquals(
    expected: List<Point>,
    actual: List<Point>,
) {
    assertEquals(expected.size, actual.size)
    expected.zip(actual).forEach { (e, a) -> e.zip(a).forEach { (x, y) -> assertEquals(x, y, absoluteTolerance = 1e-9) } }
}

private fun twoGroups(): List<Point> = listOf(listOf(0.0, 0.0), listOf(0.0, 1.0), listOf(10.0, 10.0), listOf(10.0, 11.0))

class KMeansTest {
    @Test
    fun `割り当てが変わらなくなるまで割り当てと中心の更新を繰り返す`() {
        val result = kmeans(twoGroups(), initialCenters = listOf(listOf(0.0, 0.0), listOf(0.0, 1.0)))

        assertEquals(KMeansResult(labels = listOf(0, 0, 1, 1), centers = listOf(listOf(0.0, 0.5), listOf(10.0, 10.5)), sse = 1.0), result)
    }

    @Test
    fun `最大反復回数に達したら収束していなくても打ち切る`() {
        val result = kmeans(twoGroups(), initialCenters = listOf(listOf(0.0, 0.0), listOf(0.0, 1.0)), maxIterations = 1)

        assertPointsEquals(listOf(listOf(0.0, 0.0), listOf(20.0 / 3, 22.0 / 3)), result.centers)
        assertEquals(listOf(0, 0, 1, 1), result.labels)
    }
}

private fun numberedPoints(size: Int): List<Point> = (0 until size).map { listOf(it.toDouble(), it * 2.0) }

class ChooseInitialCentersTest {
    @Test
    fun `データの中から重複なくクラスタ数だけ点を選ぶ`() {
        val points = numberedPoints(10)

        val centers = chooseInitialCenters(points, nClusters = 3, seed = 0)

        assertEquals(3, centers.toSet().size)
        assertTrue(points.containsAll(centers))
    }

    @Test
    fun `同じシードなら同じ点を選ぶ`() {
        val points = numberedPoints(10)

        assertEquals(chooseInitialCenters(points, nClusters = 3, seed = 42), chooseInitialCenters(points, nClusters = 3, seed = 42))
    }

    @Test
    fun `シードが違えば違う点を選ぶ`() {
        val points = numberedPoints(10)

        assertNotEquals(chooseInitialCenters(points, nClusters = 3, seed = 0), chooseInitialCenters(points, nClusters = 3, seed = 1))
    }
}

class SseByClusterCountTest {
    @Test
    fun `クラスタ数ごとにクラスタリングしたときのSSEを求める`() {
        assertEquals(mapOf(1 to 201.0, 2 to 1.0), sseByClusterCount(twoGroups(), clusterCounts = listOf(1, 2), seed = 0))
    }

    @Test
    fun `初期中心を変えて繰り返し最小のSSEを使う`() {
        assertEquals(mapOf(3 to 1.5), sseByClusterCount(threePairs(), clusterCounts = listOf(3), seed = 0, nInit = 10))
    }
}

private fun threePairs(): List<Point> = listOf(0.0, 1.0, 10.0, 11.0, 20.0, 21.0).map { listOf(it) }

class BestKMeansTest {
    @Test
    fun `初期中心によっては局所解に陥る`() {
        val stuck = kmeans(threePairs(), initialCenters = listOf(listOf(0.0), listOf(1.0), listOf(10.0)))

        assertEquals(101.0, stuck.sse)
    }

    @Test
    fun `複数の初期中心の候補のうちSSEが最小の結果を返す`() {
        val candidates =
            listOf(
                listOf(listOf(0.0), listOf(1.0), listOf(10.0)),
                listOf(listOf(0.0), listOf(10.0), listOf(20.0)),
            )

        val result = bestKMeans(threePairs(), candidates)

        assertEquals(1.5, result.sse)
        assertEquals(listOf(listOf(0.5), listOf(10.5), listOf(20.5)), result.centers)
    }
}

class SummarizeClustersTest {
    @Test
    fun `クラスタごとの件数と平均を件数の多い順に並べる`() {
        val df = dataFrameOf("Fresh" to listOf(100, 300, 1000), "Milk" to listOf(20, 40, 900))

        val summary = summarizeClusters(df, labels = listOf(1, 1, 0))

        assertEquals(
            listOf(
                ClusterSummary(cluster = 1, count = 2, means = mapOf("Fresh" to 200.0, "Milk" to 30.0)),
                ClusterSummary(cluster = 0, count = 1, means = mapOf("Fresh" to 1000.0, "Milk" to 900.0)),
            ),
            summary,
        )
    }
}

class WholesaleDataTest {
    private val csvFile = File(dataDir(), "Wholesale.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ Wholesale.csv が配置されていない（gulp data:setup）")
    }

    @Test
    fun `実データから440件の支出額6列を読み込む`() {
        val df = loadSpending(csvFile)

        assertEquals(440 to 6, df.rowsCount() to df.columnsCount())
    }

    @Test
    fun `標準化したデータのクラスタ数1のSSEは件数と列数の積になる`() {
        val sse = sseByClusterCount(standardize(loadSpending(csvFile)), clusterCounts = listOf(1), seed = 0)

        assertEquals(440.0 * 6, sse.getValue(1), absoluteTolerance = 1e-6)
    }

    @Test
    fun `クラスタ数を増やすほどSSEが小さくなる`() {
        val sse = sseByClusterCount(standardize(loadSpending(csvFile)), clusterCounts = (1..10).toList(), seed = 0)

        assertTrue(sse.values.zipWithNext().all { (before, after) -> before > after })
    }

    @Test
    fun `実行するとSSEとクラスタごとの件数と平均支出額を表示する`() {
        val output = captureStdout { main() }

        assertEquals(
            "データ件数: 440（支出額 6 列）\n" +
                "クラスタ数ごとの SSE（初期中心 10 通りの最小値）:\n" +
                "クラスタ数\t自作\tTribuo（k-means++）\n" +
                "1\t2640.00\t2640.00\n" +
                "2\t1954.65\t1954.78\n" +
                "3\t1610.17\t1607.67\n" +
                "4\t1352.73\t1317.90\n" +
                "5\t1134.75\t1058.77\n" +
                "6\t990.36\t917.67\n" +
                "7\t890.82\t839.38\n" +
                "8\t754.00\t742.02\n" +
                "9\t714.67\t655.14\n" +
                "10\t610.66\t606.81\n" +
                "\n" +
                "クラスタ数 5 のクラスタごとの件数と平均支出額:\n" +
                "クラスタ\t件数\tFresh\tMilk\tGrocery\tFrozen\tDetergents_Paper\tDelicassen\n" +
                "0\t269\t9115\t2954\t3786\t2277\t979\t976\n" +
                "4\t96\t5509\t10556\t16478\t1420\t7199\t1659\n" +
                "2\t61\t33029\t5058\t5600\t8507\t892\t2071\n" +
                "3\t10\t15965\t34709\t48537\t3055\t24875\t2943\n" +
                "1\t4\t31194\t21666\t17837\t13343\t2582\t23322\n",
            output,
        )
    }
}
