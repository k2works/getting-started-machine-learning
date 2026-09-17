package chapter08

import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.io.ColType
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.File

val FEATURES = listOf("Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked")
const val TARGET = "Survived"

fun loadSurvived(csvFile: File): AnyFrame = DataFrame.readCSV(csvFile, colTypes = mapOf("Embarked" to ColType.String))

fun splitFeaturesAndTarget(df: AnyFrame): Pair<AnyFrame, List<Int>> =
    dataFrameOf(FEATURES.map { df[it] }) to df[TARGET].values().map { (it as Number).toInt() }
