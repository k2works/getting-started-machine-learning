package chapter14

import dataset.dataDir
import java.io.File
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

private const val SEED = 0
private const val N_INIT = 10
private val CLUSTER_COUNTS = (1..10).toList()
private const val N_CLUSTERS = 5

private fun format(
    pattern: String,
    value: Double,
): String = pattern.format(Locale.ROOT, value)

fun main() {
    // Tribuo が学習の経過を標準エラーに出すので、警告以上だけにする
    Logger.getLogger("org.tribuo").level = Level.WARNING
    val df = loadSpending(File(dataDir(), "Wholesale.csv"))
    val points = standardize(df)
    println("データ件数: ${df.rowsCount()}（支出額 ${df.columnsCount()} 列）")
    println("クラスタ数ごとの SSE（初期中心 $N_INIT 通りの最小値）:")
    println("クラスタ数\t自作\tTribuo（k-means++）")
    for ((n, sse) in sseByClusterCount(points, CLUSTER_COUNTS, SEED, N_INIT)) {
        val tribuo = tribuoBestSse(points, n, SEED.toLong(), N_INIT)
        println("$n\t${format("%.2f", sse)}\t${format("%.2f", tribuo)}")
    }

    val result = kmeansWithRestarts(points, N_CLUSTERS, SEED, N_INIT)
    println("\nクラスタ数 $N_CLUSTERS のクラスタごとの件数と平均支出額:")
    println((listOf("クラスタ", "件数") + df.columnNames()).joinToString("\t"))
    for (summary in summarizeClusters(df, result.labels)) {
        val means = df.columnNames().map { format("%.0f", summary.means.getValue(it)) }
        println((listOf(summary.cluster.toString(), summary.count.toString()) + means).joinToString("\t"))
    }
}
