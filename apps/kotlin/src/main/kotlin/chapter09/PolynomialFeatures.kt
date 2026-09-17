package chapter09

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf

internal fun AnyFrame.doubles(column: String): List<Double> = this[column].values().map { (it as Number).toDouble() }

internal fun AnyFrame.selectColumns(names: List<String>): AnyFrame = dataFrameOf(names.map { this[it] })

fun polynomialFeatures(
    df: AnyFrame,
    columns: List<String>,
): AnyFrame =
    pairsWithReplacement(columns).fold(df.selectColumns(columns)) { features, (left, right) ->
        val leftValues = df.doubles(left)
        val rightValues = df.doubles(right)
        features.add(termName(left, right)) { leftValues[index()] * rightValues[index()] }
    }

fun <T> pairsWithReplacement(items: List<T>): List<Pair<T, T>> =
    items.indices.flatMap { i -> (i until items.size).map { j -> items[i] to items[j] } }

fun termName(
    left: String,
    right: String,
): String = if (left == right) "$left^2" else "$left $right"
