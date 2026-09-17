package chapter13

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

private fun bostonLike(): AnyFrame =
    dataFrameOf(
        "CRIME" to listOf("high", "low", "very_low", "low"),
        "RM" to listOf(5.0, 6.0, null, 7.0),
        "PRICE" to listOf(10.0, 20.0, 30.0, 40.0),
    )

class StandardizeBostonTest {
    @Test
    fun `CRIMEをダミー変数の列に置き換える`() {
        val df = standardizeBoston(bostonLike())

        assertEquals(listOf("RM", "PRICE", "low", "very_low"), df.columnNames())
    }

    @Test
    fun `欠損値を補完してから各列を平均0と標準偏差1にそろえる`() {
        val df = standardizeBoston(bostonLike())

        df.columns().forEach { column ->
            val values = column.values().map { (it as Number).toDouble() }
            val mean = values.average()
            val std = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
            assertEquals(0.0, mean, absoluteTolerance = 1e-9, message = column.name())
            assertEquals(1.0, std, absoluteTolerance = 1e-9, message = column.name())
        }
    }
}
