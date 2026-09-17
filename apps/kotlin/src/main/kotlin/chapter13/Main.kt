package chapter13

import chapter07.toMatrix
import dataset.dataDir
import java.io.File
import java.util.Locale

private const val THRESHOLD = 0.8
private const val TOP_K = 3
private const val COMPONENTS_TO_EXPLAIN = 2

private fun formatLoadings(loadings: List<Pair<String, Double>>): String =
    loadings.joinToString(", ") { (column, value) -> "$column ${"%.3f".format(Locale.ROOT, value)}" }

fun main() {
    val df = loadStandardizedBoston(File(dataDir(), "Boston.csv"))
    val columns = df.columnNames()
    val model = fitPca(df.toMatrix(columns), nComponents = columns.size)
    val ratios = model.explainedVarianceRatio
    val needed = componentsNeeded(ratios, THRESHOLD)
    println("データ件数: ${df.rowsCount()}, 列数: ${columns.size}")
    println("寄与率: " + ratios.take(needed).withIndex().joinToString(", ") { (i, r) -> "PC${i + 1} ${"%.4f".format(Locale.ROOT, r)}" })
    val cumulative = ratios.take(needed).sum()
    println("累積寄与率が $THRESHOLD に届く主成分の数: $needed（累積寄与率 ${"%.4f".format(Locale.ROOT, cumulative)}）")
    for (i in 0 until COMPONENTS_TO_EXPLAIN) {
        val loadings = topLoadings(model.components.rows[i], columns, k = TOP_K)
        println("第 ${i + 1} 主成分で影響の大きい列: ${formatLoadings(loadings)}")
    }
}
