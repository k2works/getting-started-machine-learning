package chapter09

import chapter02.TrainTestSplit
import chapter02.columnMeans
import chapter02.fillMissing
import chapter02.splitTrainTest
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.convert
import org.jetbrains.kotlinx.dataframe.api.remove
import org.jetbrains.kotlinx.dataframe.api.with
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.File

const val TARGET = "PRICE"

// 欠損値のある整数の列（Int?）には平均値（Double）を入れられないので、特徴量をすべて Double? にそろえる
private fun AnyFrame.withDoubleColumns(): AnyFrame =
    columnNames().fold(this) { df, name -> df.convert(name).with { (it as Number?)?.toDouble() } }

fun prepareBoston(
    csvFile: File,
    testSize: Double,
    seed: Int,
): TrainTestSplit<Double> {
    val df = DataFrame.readCSV(csvFile)
    val encoded = encodeDummies(df, "CRIME", dummyCategories(df["CRIME"].values().map { it as String? }))
    val x = encoded.remove(TARGET).withDoubleColumns()
    val split = splitTrainTest(x, encoded.doubles(TARGET), testSize, seed)
    val means = columnMeans(split.xTrain, x.columnNames())
    return split.copy(
        xTrain = fillMissing(split.xTrain, means),
        xTest = fillMissing(split.xTest, means),
    )
}

fun scoreFeatureSet(
    split: TrainTestSplit<Double>,
    columns: List<String>,
    terms: List<String>,
): Pair<Double, Double> {
    val train = polynomialFeatures(split.xTrain, columns).selectColumns(terms)
    val test = polynomialFeatures(split.xTest, columns).selectColumns(terms)
    val standardizer = Standardizer.fit(train)
    val xTrain = standardizer.transform(train).toRows()
    val xTest = standardizer.transform(test).toRows()
    val model = fitLinearRegression(xTrain, split.tTrain)
    return rSquared(split.tTrain, model.predict(xTrain)) to rSquared(split.tTest, model.predict(xTest))
}
