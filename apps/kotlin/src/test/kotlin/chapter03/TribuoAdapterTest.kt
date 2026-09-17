package chapter03

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import kotlin.test.Test
import kotlin.test.assertEquals

class ToTribuoDatasetTest {
    @Test
    fun `データフレームの行を特徴量名つきの事例に変換する`() {
        val x =
            dataFrameOf(
                "花弁長さ" to listOf(0.1, 0.6),
                "花弁幅" to listOf(0.2, 0.8),
            )

        val dataset = toTribuoDataset(x, listOf("setosa", "virginica"))

        assertEquals(2, dataset.size())
        assertEquals(setOf("花弁長さ", "花弁幅"), dataset.featureIDMap.map { it.name }.toSet())
        assertEquals(
            setOf("setosa", "virginica"),
            dataset.outputInfo.domain
                .map { it.label }
                .toSet(),
        )
    }
}

class TribuoTreeTest {
    @Test
    fun `同数の葉が無ければTribuoのCARTと自作の決定木は同じ予測をする`() {
        val (x, t) = threeSpecies()
        val newX = dataFrameOf("花弁幅" to listOf(0.2, 0.4, 0.55, 0.75, 0.95))

        val model = trainTribuoTree(x, t, maxDepth = null, minChildWeight = 1.0f)

        assertEquals(DecisionTree().fit(x, t).predict(newX), predictWithTribuo(model, newX))
    }

    @Test
    fun `葉の多数決が同数のとき自作は先に現れたラベルを選ぶがTribuoは出現順に依存しない`() {
        val x = dataFrameOf("花弁幅" to listOf(0.1, 0.1))

        val mine = listOf(listOf("b", "a"), listOf("a", "b")).map { DecisionTree().fit(x, it).predict(x).first() }
        val tribuo = listOf(listOf("b", "a"), listOf("a", "b")).map { predictWithTribuo(trainTribuoTree(x, it, null, 1.0f), x).first() }

        assertEquals(listOf("b", "a"), mine)
        assertEquals(1, tribuo.toSet().size)
    }
}
