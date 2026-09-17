package chapter09

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.convert
import org.jetbrains.kotlinx.dataframe.api.mean
import org.jetbrains.kotlinx.dataframe.api.std
import org.jetbrains.kotlinx.dataframe.api.with

data class Standardizer(
    val means: Map<String, Double>,
    val stds: Map<String, Double>,
) {
    fun transform(df: AnyFrame): AnyFrame =
        means.keys.fold(df) { standardized, column ->
            standardized.convert(column).with { ((it as Number).toDouble() - means.getValue(column)) / stds.getValue(column) }
        }

    companion object {
        fun fit(df: AnyFrame): Standardizer =
            Standardizer(
                means = df.columnNames().associateWith { df[it].cast<Number>().mean() },
                stds = df.columnNames().associateWith { df[it].cast<Number>().std(ddof = 0).takeIf { std -> std != 0.0 } ?: 1.0 },
            )
    }
}
