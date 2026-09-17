package chapter07

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatrixTimesTest {
    @Test
    fun `行列の積を求める`() {
        val a = Matrix(listOf(listOf(1.0, 2.0), listOf(3.0, 4.0)))
        val b = Matrix(listOf(listOf(5.0, 6.0), listOf(7.0, 8.0)))

        assertEquals(Matrix(listOf(listOf(19.0, 22.0), listOf(43.0, 50.0))), a * b)
    }

    @Test
    fun `行数と列数が違う行列の積を求める`() {
        val a = Matrix(listOf(listOf(1.0, 2.0, 3.0), listOf(4.0, 5.0, 6.0)))
        val b = Matrix(listOf(listOf(1.0), listOf(0.0), listOf(2.0)))

        assertEquals(Matrix(listOf(listOf(7.0), listOf(16.0))), a * b)
    }

    @Test
    fun `左の列数と右の行数が違えば積を求められない`() {
        val a = Matrix(listOf(listOf(1.0, 2.0)))

        val error = assertFailsWith<IllegalArgumentException> { a * a }

        assertEquals("左の行列の列数 2 と右の行列の行数 1 が違います", error.message)
    }
}

class MatrixTransposeTest {
    @Test
    fun `行と列を入れ替える`() {
        val a = Matrix(listOf(listOf(1.0, 2.0, 3.0), listOf(4.0, 5.0, 6.0)))

        assertEquals(Matrix(listOf(listOf(1.0, 4.0), listOf(2.0, 5.0), listOf(3.0, 6.0))), a.transpose())
    }
}

private fun assertMatrixEquals(
    expected: Matrix,
    actual: Matrix,
) {
    assertEquals(expected.rows.size, actual.rows.size)
    expected.rows
        .flatten()
        .zip(actual.rows.flatten())
        .forEach { (e, a) -> assertEquals(e, a, absoluteTolerance = 1e-9) }
}

class MatrixSolveTest {
    @Test
    fun `連立方程式の解を求める`() {
        val a = Matrix(listOf(listOf(2.0, 1.0), listOf(1.0, 3.0)))
        val b = Matrix(listOf(listOf(3.0), listOf(5.0)))

        assertMatrixEquals(Matrix(listOf(listOf(0.8), listOf(1.4))), a.solve(b))
    }

    @Test
    fun `3元の連立方程式の解を求める`() {
        val a = Matrix(listOf(listOf(4.0, 1.0, 2.0), listOf(1.0, 3.0, 0.0), listOf(2.0, 0.0, 5.0)))
        val b = a * Matrix(listOf(listOf(1.0), listOf(-2.0), listOf(3.0)))

        assertMatrixEquals(Matrix(listOf(listOf(1.0), listOf(-2.0), listOf(3.0))), a.solve(b))
    }

    @Test
    fun `対角成分が0でも行を入れ替えて解を求める`() {
        val a = Matrix(listOf(listOf(0.0, 1.0), listOf(1.0, 0.0)))
        val b = Matrix(listOf(listOf(2.0), listOf(3.0)))

        assertMatrixEquals(Matrix(listOf(listOf(3.0), listOf(2.0))), a.solve(b))
    }
}
