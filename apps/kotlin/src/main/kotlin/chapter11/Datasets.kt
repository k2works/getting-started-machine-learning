package chapter11

import chapter02.columnMeans
import chapter02.fillMissing
import chapter07.FEATURES
import chapter07.TARGET
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.convertToDouble
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf

fun prepareSurvived(df: AnyFrame): Pair<AnyFrame, List<String>> {
    val features =
        dataFrameOf(
            "Pclass" to df["Pclass"].toList(),
            "Age" to df["Age"].toList(),
            "male" to df["Sex"].values().map { if (it == "male") 1 else 0 },
        )
    val x = fillMissing(features, columnMeans(features, listOf("Age")))
    return x to df["Survived"].values().map { it.toString() }
}

fun prepareCinema(df: AnyFrame): Pair<AnyFrame, List<Double>> {
    val features = dataFrameOf(FEATURES.map { df[it].convertToDouble() })
    val x = fillMissing(features, columnMeans(features, FEATURES))
    return x to df[TARGET].values().map { (it as Number).toDouble() }
}
