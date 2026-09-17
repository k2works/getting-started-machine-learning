package chapter10

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class MajorityVoteTest {
    @Test
    fun `サンプルごとに最も多い予測を選ぶ`() {
        val votes =
            listOf(
                listOf("setosa", "virginica"),
                listOf("setosa", "virginica"),
                listOf("versicolor", "setosa"),
            )

        assertEquals(listOf("setosa", "virginica"), majorityVote(votes))
    }
}

class BootstrapSampleTest {
    @Test
    fun `元のデータと同じ件数の行番号を重複を許して選ぶ`() {
        val rows = bootstrapSample(100, Random(0))

        assertEquals(100, rows.size)
        assertTrue(rows.all { it in 0 until 100 })
        assertTrue(rows.toSet().size < 100)
    }

    @Test
    fun `同じシードなら同じ行を選ぶ`() {
        assertEquals(bootstrapSample(10, Random(42)), bootstrapSample(10, Random(42)))
    }
}

internal fun twoSpecies(): Pair<AnyFrame, List<String>> {
    val x =
        dataFrameOf(
            "がく片幅" to listOf(0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4, 0.5, 0.3),
            "花弁幅" to listOf(0.1, 0.12, 0.14, 0.16, 0.18, 0.8, 0.82, 0.84, 0.86, 0.88),
        )
    val t = List(5) { "setosa" } + List(5) { "virginica" }
    return x to t
}

class RandomForestTest {
    @Test
    fun `指定した数だけ第3章の決定木を学習する`() {
        val (x, t) = twoSpecies()

        val model = RandomForest(nEstimators = 5, maxFeatures = 1, seed = 0).fit(x, t)

        assertEquals(5, model.trees.size)
    }

    @Test
    fun `各決定木は指定した数の特徴量だけを使う`() {
        val (x, t) = twoSpecies()

        val model = RandomForest(nEstimators = 5, maxFeatures = 1, seed = 0).fit(x, t)

        assertTrue(model.trees.all { it.columns.size == 1 })
    }

    @Test
    fun `決定木の多数決で予測する`() {
        val (x, t) = twoSpecies()

        val model = RandomForest(nEstimators = 25, maxFeatures = 2, seed = 0).fit(x, t)

        val newX =
            dataFrameOf(
                "がく片幅" to listOf(0.4, 0.4),
                "花弁幅" to listOf(0.13, 0.83),
            )
        assertEquals(listOf("setosa", "virginica"), model.predict(newX))
    }

    @Test
    fun `同じシードなら同じ予測になる`() {
        val (x, t) = twoSpecies()

        val first = RandomForest(nEstimators = 5, maxFeatures = 1, seed = 7).fit(x, t)
        val second = RandomForest(nEstimators = 5, maxFeatures = 1, seed = 7).fit(x, t)

        assertEquals(first.trees.map { it.columns }, second.trees.map { it.columns })
        assertEquals(first.predict(x), second.predict(x))
    }

    @Test
    fun `学習する前に予測するとエラーになる`() {
        val error = assertFailsWith<IllegalStateException> { RandomForest().predict(dataFrameOf("花弁幅" to listOf(0.1))) }

        assertEquals("fit で学習してから predict を呼んでください", error.message)
    }
}
