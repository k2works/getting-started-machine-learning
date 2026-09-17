package chapter12

import chapter07.Matrix
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatrixOperationsTest {
    @Test
    fun `同じ形の行列を要素ごとに足す`() {
        val a = Matrix(listOf(listOf(1.0, 2.0), listOf(3.0, 4.0)))
        val b = Matrix(listOf(listOf(10.0, 20.0), listOf(30.0, 40.0)))

        assertEquals(Matrix(listOf(listOf(11.0, 22.0), listOf(33.0, 44.0))), a + b)
    }

    @Test
    fun `形が違う行列は足せない`() {
        val a = Matrix(listOf(listOf(1.0, 2.0)))
        val b = Matrix(listOf(listOf(1.0), listOf(2.0)))

        val error = assertFailsWith<IllegalArgumentException> { a + b }

        assertEquals("1 行 2 列の行列と 2 行 1 列の行列は足せません", error.message)
    }

    @Test
    fun `数と行列の積は各要素に数を掛ける`() {
        val a = Matrix(listOf(listOf(1.0, -2.0), listOf(0.5, 4.0)))

        assertEquals(Matrix(listOf(listOf(2.0, -4.0), listOf(1.0, 8.0))), 2.0 * a)
    }

    @Test
    fun `単位行列は対角成分が1でそれ以外が0の正方行列`() {
        assertEquals(Matrix(listOf(listOf(1.0, 0.0, 0.0), listOf(0.0, 1.0, 0.0), listOf(0.0, 0.0, 1.0))), identity(3))
    }
}
