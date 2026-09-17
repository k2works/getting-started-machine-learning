package chapter13

import chapter07.Matrix
import java.util.Random
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals

private fun assertMatrixEquals(
    expected: List<List<Double>>,
    actual: Matrix,
) {
    assertEquals(expected.size, actual.rows.size)
    expected.zip(actual.rows).forEach { (expectedRow, actualRow) ->
        assertEquals(expectedRow.size, actualRow.size)
        expectedRow.zip(actualRow).forEach { (e, a) -> assertEquals(e, a, absoluteTolerance = 1e-9) }
    }
}

class CovarianceMatrixTest {
    @Test
    fun `2列の分散と共分散を並べた行列を返す`() {
        val x = Matrix(listOf(listOf(1.0, 2.0), listOf(3.0, 6.0), listOf(5.0, 10.0)))

        assertMatrixEquals(listOf(listOf(4.0, 8.0), listOf(8.0, 16.0)), covarianceMatrix(x))
    }

    @Test
    fun `3列でも各列の分散と2列ずつの共分散を並べる`() {
        val x = Matrix(listOf(listOf(1.0, 2.0, 0.0), listOf(3.0, 6.0, 1.0), listOf(5.0, 10.0, 5.0)))

        assertMatrixEquals(
            listOf(listOf(4.0, 8.0, 5.0), listOf(8.0, 16.0, 10.0), listOf(5.0, 10.0, 7.0)),
            covarianceMatrix(x),
        )
    }
}

private fun assertListEquals(
    expected: List<Double>,
    actual: List<Double>,
) {
    assertEquals(expected.size, actual.size)
    expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, absoluteTolerance = 1e-9) }
}

class FitPcaTest {
    @Test
    fun `完全に相関する2列なら第1主成分だけで分散をすべて説明する`() {
        val x = Matrix(listOf(listOf(1.0, 2.0), listOf(3.0, 6.0), listOf(5.0, 10.0)))

        val model = fitPca(x, nComponents = 2)

        assertListEquals(listOf(1 / sqrt(5.0), 2 / sqrt(5.0)), model.components.rows[0])
        assertListEquals(listOf(1.0, 0.0), model.explainedVarianceRatio)
    }

    @Test
    fun `主成分は寄与率の大きい順に指定した数だけ並ぶ`() {
        val model = fitPca(mixedDataset(), nComponents = 3)

        val ratios = model.explainedVarianceRatio
        assertEquals(3, ratios.size)
        assertEquals(ratios.sortedDescending(), ratios)
    }

    @Test
    fun `主成分の向きは絶対値が最大の要素が正になるようにそろえる`() {
        val x = Matrix(listOf(listOf(1.0, 2.0), listOf(3.0, 6.0), listOf(5.0, 10.0)))

        val model = fitPca(x, nComponents = 2)

        assertListEquals(listOf(2 / sqrt(5.0), -1 / sqrt(5.0)), model.components.rows[1])
    }

    @Test
    fun `主成分は長さ1で互いに直交する`() {
        val model = fitPca(mixedDataset(), nComponents = 3)

        val gram = model.components * model.components.transpose()

        assertMatrixEquals(List(3) { i -> List(3) { j -> if (i == j) 1.0 else 0.0 } }, gram)
    }

    @Test
    fun `主成分は分散共分散行列に掛けると分散の倍数になる固有ベクトル`() {
        val x = mixedDataset()

        val model = fitPca(x, nComponents = 3)

        val covariance = covarianceMatrix(x)
        model.components.rows.zip(model.explainedVariance).forEach { (component, variance) ->
            val projected = covariance * Matrix(component.map { listOf(it) })
            assertListEquals(component.map { it * variance }, projected.columns.first())
        }
    }
}

class NormalizeSignsTest {
    @Test
    fun `絶対値が最大の要素が正になるように主成分の向きをそろえる`() {
        val components = Matrix(listOf(listOf(0.6, -0.8), listOf(-0.8, 0.6)))

        assertMatrixEquals(listOf(listOf(-0.6, 0.8), listOf(0.8, -0.6)), normalizeSigns(components))
    }
}

/** 2 つの隠れた変数を 4 列に混ぜ、小さなノイズを加えた 40 行の架空のデータ */
private fun mixedDataset(): Matrix {
    val random = Random(0)
    val mixing = listOf(listOf(2.0, 0.5), listOf(0.3, 1.0), listOf(1.0, -1.0), listOf(0.0, 0.2))
    return Matrix(
        List(40) {
            val base = listOf(random.nextGaussian(), random.nextGaussian())
            mixing.map { weights -> weights.zip(base).sumOf { (w, b) -> w * b } + random.nextGaussian() * 0.1 }
        },
    )
}

class TransformTest {
    @Test
    fun `平均を引いてから主成分の向きに射影する`() {
        val model =
            PcaModel(
                mean = listOf(1.0, 2.0),
                components = Matrix(listOf(listOf(0.6, 0.8))),
                explainedVariance = listOf(1.0),
                explainedVarianceRatio = listOf(1.0),
            )

        assertMatrixEquals(listOf(listOf(1.4), listOf(0.0)), transform(model, Matrix(listOf(listOf(2.0, 3.0), listOf(1.0, 2.0)))))
    }
}

class ComponentsNeededTest {
    @Test
    fun `累積寄与率がしきい値に届くまでの主成分の数を返す`() {
        assertEquals(2, componentsNeeded(listOf(0.5, 0.25, 0.25), threshold = 0.75))
    }

    @Test
    fun `しきい値を上げると必要な主成分の数が増える`() {
        assertEquals(3, componentsNeeded(listOf(0.5, 0.25, 0.25), threshold = 0.8))
    }
}

class TopLoadingsTest {
    @Test
    fun `係数の絶対値が大きい順に列名と係数を返す`() {
        val component = listOf(0.1, -0.7, 0.5)

        assertEquals(listOf("DIS" to -0.7, "TAX" to 0.5), topLoadings(component, listOf("ZN", "DIS", "TAX"), k = 2))
    }
}
