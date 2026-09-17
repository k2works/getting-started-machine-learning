package chapter13

import org.jetbrains.kotlinx.dataframe.AnyCol
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.remove
import org.jetbrains.kotlinx.dataframe.api.toColumn
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.File
import kotlin.math.sqrt

private const val CRIME = "CRIME"

/** 数値の列を Double に変換し、欠損値を列の平均値で補完する */
private fun AnyCol.toFilledDoubles(): List<Double> {
    val numbers = values().map { (it as Number?)?.toDouble() }
    val mean = numbers.filterNotNull().average()
    return numbers.map { it ?: mean }
}

/** 平均 0・標準偏差 1 にそろえる（標準偏差は件数 n で割る） */
private fun standardize(values: List<Double>): List<Double> {
    val mean = values.average()
    val std = sqrt(values.sumOf { (it - mean) * (it - mean) } / values.size)
    return values.map { (it - mean) / std }
}

fun standardizeBoston(df: AnyFrame): AnyFrame {
    val crime = df[CRIME].values().map { it.toString() }
    val numeric = df.remove(CRIME).columns().map { it.name() to it.toFilledDoubles() }
    val dummies =
        crime.distinct().sorted().drop(1).map { category ->
            category to crime.map { if (it == category) 1.0 else 0.0 }
        }
    return (numeric + dummies).map { (name, values) -> standardize(values).toColumn(name) }.toDataFrame()
}

fun loadStandardizedBoston(csvFile: File): AnyFrame = standardizeBoston(DataFrame.readCSV(csvFile))
