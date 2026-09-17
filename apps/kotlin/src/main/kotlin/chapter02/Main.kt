package chapter02

import dataset.dataDir
import java.io.File

private const val TEST_SIZE = 0.3
private const val SEED = 0

private fun formatCounts(counts: Map<String, Int>): String = counts.entries.joinToString(", ") { (column, count) -> "$column=$count" }

fun main() {
    val csvFile = File(dataDir(), "iris.csv")
    val df = loadIris(csvFile)
    val split = prepareIris(csvFile, testSize = TEST_SIZE, seed = SEED)
    println("データ件数: ${df.rowsCount()}")
    println("欠損値の数: ${formatCounts(countMissing(df))}")
    println("訓練データ: ${split.xTrain.rowsCount()} 件, テストデータ: ${split.xTest.rowsCount()} 件")
    val missingTrain = countMissing(split.xTrain).values.sum()
    val missingTest = countMissing(split.xTest).values.sum()
    println("補完後の欠損値の数: 訓練データ $missingTrain, テストデータ $missingTest")
}
