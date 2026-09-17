package chapter09

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.groupBy
import org.jetbrains.kotlinx.dataframe.api.innerJoin
import org.jetbrains.kotlinx.dataframe.api.mean
import org.jetbrains.kotlinx.dataframe.api.rows
import org.jetbrains.kotlinx.dataframe.api.sortByDesc
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.File
import java.nio.charset.Charset

fun loadBike(tsvFile: File): AnyFrame = DataFrame.readCSV(tsvFile, delimiter = '\t')

fun loadWeather(csvFile: File): AnyFrame = DataFrame.readCSV(csvFile, charset = Charset.forName("Shift_JIS"))

fun joinWeather(
    bike: AnyFrame,
    weather: AnyFrame,
): AnyFrame = bike.innerJoin(weather, "weather_id")

fun meanCountByWeather(joined: AnyFrame): Map<String, Double> =
    joined
        .groupBy("weather")
        .mean("cnt")
        .sortByDesc("cnt")
        .rows()
        .associate { it["weather"] as String to (it["cnt"] as Number).toDouble() }
