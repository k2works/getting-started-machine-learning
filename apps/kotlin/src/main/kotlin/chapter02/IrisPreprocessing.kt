package chapter02

import org.jetbrains.kotlinx.dataframe.AnyCol
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.api.fillNulls
import org.jetbrains.kotlinx.dataframe.api.remove
import org.jetbrains.kotlinx.dataframe.api.with
import org.jetbrains.kotlinx.dataframe.io.readCSV
import java.io.File
import kotlin.math.ceil
import kotlin.random.Random

fun loadIris(csvFile: File): AnyFrame = DataFrame.readCSV(csvFile)

private fun AnyCol.countNulls(): Int = values().count { it == null }

fun countMissing(df: AnyFrame): Map<String, Int> = df.columns().associate { it.name() to it.countNulls() }

fun columnMeans(
    df: AnyFrame,
    columns: List<String>,
): Map<String, Double> =
    columns.associateWith { name ->
        df[name]
            .values()
            .filterIsInstance<Number>()
            .map { it.toDouble() }
            .average()
    }

fun fillMissing(
    df: AnyFrame,
    values: Map<String, Double>,
): AnyFrame = values.entries.fold(df) { filled, (name, value) -> filled.fillNulls(name).with { value } }

fun splitFeaturesAndTarget(
    df: AnyFrame,
    target: String,
): Pair<AnyFrame, List<String>> = df.remove(target) to df[target].values().map { it.toString() }

data class TrainTestSplit<T>(
    val xTrain: AnyFrame,
    val xTest: AnyFrame,
    val tTrain: List<T>,
    val tTest: List<T>,
)

fun <T> splitTrainTest(
    x: AnyFrame,
    t: List<T>,
    testSize: Double,
    seed: Int,
): TrainTestSplit<T> {
    val positions = (0 until x.rowsCount()).shuffled(Random(seed))
    val nTrain = x.rowsCount() - ceil(x.rowsCount() * testSize).toInt()
    val train = positions.take(nTrain)
    val test = positions.drop(nTrain)
    return TrainTestSplit(
        xTrain = x[train],
        xTest = x[test],
        tTrain = t.slice(train),
        tTest = t.slice(test),
    )
}

const val TARGET = "種類"

fun prepareIris(
    csvFile: File,
    testSize: Double,
    seed: Int,
): TrainTestSplit<String> {
    val (x, t) = splitFeaturesAndTarget(loadIris(csvFile), TARGET)
    val split = splitTrainTest(x, t, testSize, seed)
    val means = columnMeans(split.xTrain, x.columnNames())
    return split.copy(
        xTrain = fillMissing(split.xTrain, means),
        xTest = fillMissing(split.xTest, means),
    )
}
