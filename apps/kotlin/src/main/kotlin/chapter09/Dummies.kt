package chapter09

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.add
import org.jetbrains.kotlinx.dataframe.api.remove

fun dummyCategories(values: List<String?>): List<String> =
    values
        .filterNotNull()
        .distinct()
        .sorted()
        .drop(1)

fun encodeDummies(
    df: AnyFrame,
    column: String,
    categories: List<String>,
): AnyFrame =
    categories.fold(df.remove(column)) { encoded, category ->
        val flags = df[column].values().map { if (it == category) 1 else 0 }
        encoded.add("${column}_$category") { flags[index()] }
    }
