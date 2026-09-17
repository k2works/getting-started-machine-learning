package chapter11

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.test.Test
import kotlin.test.assertEquals

class PrepareSurvivedTest {
    @Test
    fun `客室クラスと年齢と男性かどうかを特徴量にし生存を正解ラベルにする`() {
        val df =
            dataFrameOf(
                "PassengerId" to listOf(1, 2),
                "Survived" to listOf(0, 1),
                "Pclass" to listOf(3, 1),
                "Sex" to listOf("male", "female"),
                "Age" to listOf(30.0, 40.0),
                "Fare" to listOf(8.0, 60.0),
            )

        val (x, t) = prepareSurvived(df)

        assertEquals(listOf("Pclass", "Age", "male"), x.columnNames())
        assertEquals(listOf(listOf<Any?>(3, 1), listOf<Any?>(30.0, 40.0), listOf<Any?>(1, 0)), x.columns().map { it.toList() })
        assertEquals(listOf("0", "1"), t)
    }

    @Test
    fun `年齢の欠損値を年齢の平均値で補完する`() {
        val df =
            dataFrameOf(
                "Survived" to listOf(0, 1, 1),
                "Pclass" to listOf(3, 1, 2),
                "Sex" to listOf("male", "female", "female"),
                "Age" to listOf(20.0, null, 40.0),
            )

        val (x, _) = prepareSurvived(df)

        assertEquals(listOf(20.0, 30.0, 40.0), x["Age"].toList())
    }
}

class PrepareCinemaTest {
    @Test
    fun `興行収入を正解ラベルにし特徴量の欠損値を平均値で補完する`() {
        val df =
            dataFrameOf(
                "cinema_id" to listOf(101, 102, 103),
                "SNS1" to listOf(100, null, 300),
                "SNS2" to listOf(500, 600, 700),
                "actor" to listOf(null, 20.0, 40.0),
                "original" to listOf(0, 1, 0),
                "sales" to listOf(9000, 9500, 10000),
            )

        val (x, t) = prepareCinema(df)

        assertEquals(listOf("SNS1", "SNS2", "actor", "original"), x.columnNames())
        assertEquals(
            listOf(listOf(100.0, 200.0, 300.0), listOf(500.0, 600.0, 700.0), listOf(30.0, 20.0, 40.0), listOf(0.0, 1.0, 0.0)),
            x.columns().map { it.toList() },
        )
        assertEquals(listOf(9000.0, 9500.0, 10000.0), t)
    }
}
