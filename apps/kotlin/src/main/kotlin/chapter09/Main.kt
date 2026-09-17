package chapter09

import dataset.dataDir
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.mean
import org.jetbrains.kotlinx.dataframe.api.std
import java.io.File
import java.util.Locale
import kotlin.math.abs

private const val TEST_SIZE = 0.3
private const val SEED = 0

// 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす
private const val ZERO_TOLERANCE = 1e-9
private const val SCORE_DIGITS = 4
private const val MEAN_DIGITS = 2
private const val COUNT_DIGITS = 1

val COLUMNS = listOf("RM", "LSTAT", "PTRATIO")
val SQUARES = listOf("RM^2", "LSTAT^2", "PTRATIO^2")
val FEATURE_SETS =
    linkedMapOf(
        "元の特徴量" to COLUMNS,
        "2 乗の項を追加" to COLUMNS + SQUARES,
        "交互作用の項も追加" to COLUMNS + pairsWithReplacement(COLUMNS).map { (left, right) -> termName(left, right) },
    )

private fun format(
    value: Double,
    digits: Int,
): String = "%.${digits}f".format(Locale.ROOT, if (abs(value) < ZERO_TOLERANCE) 0.0 else value)

private fun formatScores(scores: Pair<Double, Double>): String =
    "訓練 ${format(scores.first, SCORE_DIGITS)}, テスト ${format(scores.second, SCORE_DIGITS)}"

fun main() {
    val split = prepareBoston(File(dataDir(), "Boston.csv"), testSize = TEST_SIZE, seed = SEED)
    println("訓練データ: ${split.xTrain.rowsCount()} 件, テストデータ: ${split.xTest.rowsCount()} 件")
    println("特徴量の列: ${split.xTrain.columnNames().joinToString(", ")}")

    val rm = Standardizer.fit(split.xTrain).transform(split.xTrain)["RM"].cast<Double>()
    println("標準化した訓練データの RM: 平均 ${format(rm.mean(), MEAN_DIGITS)}, 標準偏差 ${format(rm.std(ddof = 0), MEAN_DIGITS)}")

    println("決定係数:")
    for ((name, terms) in FEATURE_SETS) {
        println("  $name（${terms.size} 列）: ${formatScores(scoreFeatureSet(split, COLUMNS, terms))}")
    }

    println("訓練データの PRICE の外れ値: ${iqrOutliers(split.tTrain).count { it }} 件")
    val removed = scoreFeatureSet(removeTargetOutliers(split), COLUMNS, COLUMNS + SQUARES)
    println("  外れ値を除いて 2 乗の項を追加: ${formatScores(removed)}")

    val joined = joinWeather(loadBike(File(dataDir(), "bike.tsv")), loadWeather(File(dataDir(), "weather.csv")))
    val means = meanCountByWeather(joined)
    println("天気ごとの平均利用者数: " + means.entries.joinToString(", ") { (weather, count) -> "$weather=${format(count, COUNT_DIGITS)}" })
}
