package chapter12

import chapter07.r2Score
import dataset.dataDir
import org.tribuo.regression.slm.ElasticNetCDTrainer
import java.io.File
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

private const val TEST_SIZE = 0.3
private const val VALIDATION_SIZE = 0.3
private const val SEED = 0
private val ALPHAS = listOf(0.0, 0.1, 1.0, 10.0, 100.0)
private const val LASSO_ALPHA = 0.5

/** 表の alpha の列の幅（100.0 が収まる文字数） */
private const val ALPHA_WIDTH = 5

private fun format(
    pattern: String,
    value: Double,
): String = pattern.format(Locale.ROOT, value)

fun main() {
    // Tribuo の座標降下法が収束のたびに出す INFO のログを表示しない
    Logger.getLogger(ElasticNetCDTrainer::class.java.name).level = Level.WARNING
    val csvFile = File(dataDir(), "Boston.csv")
    val df = loadBoston(csvFile)
    val kept = removeOutliers(df, FEATURES + TARGET, OUTLIER_THRESHOLD)
    val dataset = prepareBoston(csvFile, TEST_SIZE, VALIDATION_SIZE, SEED)
    println("データ件数: ${kept.rowsCount()}（外れ値 ${df.rowsCount() - kept.rowsCount()} 件を除外）")
    println("訓練データ: ${dataset.tTrain.size} 件, 検証データ: ${dataset.tValid.size} 件, テストデータ: ${dataset.tTest.size} 件")
    println("特徴量: ${dataset.featureNames.joinToString(", ")}")

    val experiments = runRidgeExperiments(dataset.xTrain, dataset.tTrain, dataset.xValid, dataset.tValid, ALPHAS)
    println("alpha  訓練 R²  検証 R²  係数の絶対値の合計")
    for (e in experiments) {
        println(
            "${e.alpha.toString().padStart(ALPHA_WIDTH)}  ${format("%.4f", e.trainScore)}  " +
                "${format("%.4f", e.validationScore)}  ${format("%.3f", e.coefficientAbsSum)}",
        )
    }
    val best = bestExperiment(experiments)
    println("検証データで選んだ alpha: ${best.alpha}")

    val linear = fitRidge(dataset.xTrain, dataset.tTrain, alpha = 0.0)
    val ridge = fitRidge(dataset.xTrain, dataset.tTrain, alpha = best.alpha)
    val linearScore = r2Score(dataset.tTest, linear.predict(dataset.xTest))
    val ridgeScore = r2Score(dataset.tTest, ridge.predict(dataset.xTest))
    println("テストデータの決定係数: 線形回帰 ${format("%.4f", linearScore)}, リッジ回帰 ${format("%.4f", ridgeScore)}")

    val lasso = fitLasso(dataset.xTrain, dataset.tTrain, alpha = LASSO_ALPHA)
    val zeros = zeroCoefficientNames(lasso.coefficients, dataset.featureNames).joinToString(", ")
    println("ラッソ回帰（alpha=$LASSO_ALPHA）で係数が 0 になった特徴量: $zeros")
}
