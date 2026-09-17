package chapter08

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.convert
import org.jetbrains.kotlinx.dataframe.api.fillNulls
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.median
import org.jetbrains.kotlinx.dataframe.api.remove
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.api.to
import org.jetbrains.kotlinx.dataframe.api.with
import java.io.Serializable

/** 訓練データから変換に必要な値を求める前処理 */
interface Transformer {
    fun fit(x: AnyFrame): FittedTransformer
}

/** fit で求めた値を使ってデータを変換する前処理 */
interface FittedTransformer : Serializable {
    fun transform(x: AnyFrame): AnyFrame
}

class GroupMedianImputer(
    private val column: String,
    private val by: List<String>,
) : Transformer {
    override fun fit(x: AnyFrame): FittedTransformer {
        val medians =
            x
                .groupBy { cols { it.name() in by } }
                .median(column)
                .rows()
                .filter { it[column] != null }
                .associate { row -> by.map { row[it] } to (row[column] as Number).toDouble() }
        val overallMedian =
            x
                .convert(column)
                .to<Double?>()[column]
                .cast<Double?>()
                .median()
        return FittedGroupMedianImputer(column, by, medians, overallMedian)
    }
}

data class FittedGroupMedianImputer(
    val column: String,
    val by: List<String>,
    val medians: Map<List<Any?>, Double>,
    val overallMedian: Double,
) : FittedTransformer {
    private companion object {
        private const val serialVersionUID: Long = 1
    }

    override fun transform(x: AnyFrame): AnyFrame =
        x
            .convert(column)
            .to<Double?>()
            .fillNulls(column)
            .with { medians[by.map { name -> this[name] }] ?: overallMedian }
}

class MostFrequentImputer(
    private val column: String,
) : Transformer {
    override fun fit(x: AnyFrame): FittedTransformer {
        val mostFrequent =
            x[column]
                .values()
                .filterNotNull()
                .map { it.toString() }
                .groupingBy { it }
                .eachCount()
                .maxBy { it.value }
                .key
        return FittedMostFrequentImputer(column, mostFrequent)
    }
}

data class FittedMostFrequentImputer(
    val column: String,
    val mostFrequent: String,
) : FittedTransformer {
    private companion object {
        private const val serialVersionUID: Long = 1
    }

    override fun transform(x: AnyFrame): AnyFrame =
        x
            .convert(column)
            .to<String?>()
            .fillNulls(column)
            .with { mostFrequent }
}

class DummyEncoder(
    private val columns: List<String>,
) : Transformer {
    override fun fit(x: AnyFrame): FittedTransformer = FittedDummyEncoder(columns.associateWith { categoriesOf(x, it).drop(1) })
}

data class FittedDummyEncoder(
    val dummies: Map<String, List<String>>,
) : FittedTransformer {
    private companion object {
        private const val serialVersionUID: Long = 1
    }

    override fun transform(x: AnyFrame): AnyFrame = encode(x, dummies)
}

private fun categoriesOf(
    x: AnyFrame,
    column: String,
): List<String> =
    x[column]
        .values()
        .filterNotNull()
        .map { it.toString() }
        .distinct()
        .sorted()

private fun encode(
    x: AnyFrame,
    dummies: Map<String, List<String>>,
): AnyFrame =
    dummies.entries.fold(x) { df, (column, categories) ->
        categories
            .fold(df) { acc, category -> acc.add("${column}_$category") { if (this[column]?.toString() == category) 1 else 0 } }
            .remove(column)
    }
