package chapter14

import org.tribuo.clustering.kmeans.KMeansTrainer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TribuoKMeansTest {
    @Test
    fun `KMeansTrainerの初期化方法はRANDOMとPLUSPLUSだけで初期中心を渡すコンストラクタは無い`() {
        assertEquals(listOf("RANDOM", "PLUSPLUS"), KMeansTrainer.Initialisation.entries.map { it.name })
        assertTrue(
            KMeansTrainer::class.java.constructors.none { constructor ->
                constructor.parameterTypes.any { it.isArray || Collection::class.java.isAssignableFrom(it) }
            },
        )
    }

    @Test
    fun `はっきり分かれた2グループならTribuoのSSEも自作と同じになる`() {
        val points = listOf(listOf(0.0, 0.0), listOf(0.0, 1.0), listOf(10.0, 10.0), listOf(10.0, 11.0))

        val sse = tribuoKMeansSse(points, nClusters = 2, seed = 0L)

        assertEquals(1.0, sse, absoluteTolerance = 1e-9)
    }

    @Test
    fun `Tribuoでもシードを変えて繰り返し最小のSSEを使える`() {
        val points = listOf(0.0, 1.0, 10.0, 11.0, 20.0, 21.0).map { listOf(it) }

        assertEquals(1.5, tribuoBestSse(points, nClusters = 3, seed = 0L, nInit = 10), absoluteTolerance = 1e-9)
    }
}
