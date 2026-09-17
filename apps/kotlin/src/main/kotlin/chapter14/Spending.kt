package chapter14

import chapter09.Standardizer
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.remove
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.File

fun loadSpending(csvFile: File): AnyFrame = DataFrame.readCSV(csvFile).remove("Channel", "Region")

fun standardize(df: AnyFrame): List<Point> {
    val standardized = Standardizer.fit(df).transform(df)
    return standardized.rows().map { row -> df.columnNames().map { (row[it] as Number).toDouble() } }
}

data class ClusterSummary(
    val cluster: Int,
    val count: Int,
    val means: Map<String, Double>,
)

fun summarizeClusters(
    df: AnyFrame,
    labels: List<Int>,
): List<ClusterSummary> =
    labels.indices
        .groupBy { labels[it] }
        .map { (cluster, rows) ->
            val means = df.columnNames().associateWith { column -> rows.map { (df[column][it] as Number).toDouble() }.average() }
            ClusterSummary(cluster = cluster, count = rows.size, means = means)
        }.sortedByDescending { it.count }
