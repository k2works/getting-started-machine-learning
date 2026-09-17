package chapter07

import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MeanAbsoluteErrorTest {
    private val t = listOf(3.0, 5.0, 7.0)

    @Test
    fun `誤差の絶対値の平均を求める`() {
        assertEquals(1.0, meanAbsoluteError(t, listOf(2.0, 5.0, 9.0)), absoluteTolerance = 1e-12)
    }

    @Test
    fun `予測が大きく外れるほど値が大きくなる`() {
        assertEquals(5.0 / 3, meanAbsoluteError(t, listOf(1.0, 8.0, 7.0)), absoluteTolerance = 1e-12)
    }

    @Test
    fun `実測値と予測値の件数が違えばエラーになる`() {
        val error = assertFailsWith<IllegalArgumentException> { meanAbsoluteError(t, listOf(1.0)) }

        assertEquals("実測値と予測値の件数が違います", error.message)
    }
}

class RootMeanSquaredErrorTest {
    @Test
    fun `誤差の2乗の平均の平方根を求める`() {
        assertEquals(sqrt(5.0 / 3), rootMeanSquaredError(listOf(3.0, 5.0, 7.0), listOf(2.0, 5.0, 9.0)), absoluteTolerance = 1e-12)
    }
}

class R2ScoreTest {
    private val t = listOf(3.0, 5.0, 7.0)

    @Test
    fun `予測がすべて正解なら1になる`() {
        assertEquals(1.0, r2Score(t, listOf(3.0, 5.0, 7.0)), absoluteTolerance = 1e-12)
    }

    @Test
    fun `平均値を予測し続けるモデルより良い分だけ1に近づく`() {
        assertEquals(1 - 5.0 / 8, r2Score(t, listOf(2.0, 5.0, 9.0)), absoluteTolerance = 1e-12)
    }
}
