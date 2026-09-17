package chapter13

import org.tribuo.math.la.DenseMatrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

private fun denseMatrixOf(vararg rows: List<Double>): DenseMatrix =
    DenseMatrix.createDenseMatrix(
        rows
            .map {
                it.toDoubleArray()
            }.toTypedArray(),
    )

class TribuoEigenLearningTest {
    private val symmetric = denseMatrixOf(listOf(2.0, 1.0), listOf(1.0, 2.0))

    @Test
    fun `対称行列の固有値を大きい順に返す`() {
        val eigen = symmetric.eigenDecomposition().orElseThrow()

        assertEquals(listOf(3.0, 1.0), eigen.eigenvalues().toArray().map { Math.round(it * 1e9) / 1e9 })
    }

    @Test
    fun `対角成分の並びに関係なく固有値を大きい順に並べ替える`() {
        val diagonal = denseMatrixOf(listOf(1.0, 0.0, 0.0), listOf(0.0, 5.0, 0.0), listOf(0.0, 0.0, 3.0))

        val eigen = diagonal.eigenDecomposition().orElseThrow()

        assertEquals(listOf(5.0, 3.0, 1.0), eigen.eigenvalues().toArray().map { Math.round(it * 1e9) / 1e9 })
    }

    @Test
    fun `i番目の固有ベクトルは行列を掛けても向きが変わらずi番目の固有値倍になる`() {
        val eigen = symmetric.eigenDecomposition().orElseThrow()

        for (i in 0 until 2) {
            val v = eigen.getEigenVector(i).toArray()
            val av = symmetric.toArray().map { row -> row.zip(v.toList()).sumOf { (a, b) -> a * b } }
            val lambda = eigen.eigenvalues().get(i)
            av.zip(v.toList()).forEach { (left, right) -> assertEquals(lambda * right, left, absoluteTolerance = 1e-9) }
        }
    }

    @Test
    fun `対称でない行列は固有値分解できず空のOptionalを返す`() {
        val asymmetric = denseMatrixOf(listOf(2.0, 1.0), listOf(0.0, 2.0))

        assertFalse(asymmetric.eigenDecomposition().isPresent)
    }
}
