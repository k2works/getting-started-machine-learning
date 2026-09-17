package chapter10

import chapter02.prepareIris
import dataset.dataDir
import org.tribuo.classification.sgd.linear.LogisticRegressionTrainer
import java.io.File
import java.util.Locale
import java.util.logging.Level
import java.util.logging.Logger

private const val TEST_SIZE = 0.3
private const val SEED = 0
private const val N_ESTIMATORS = 100
private const val MAX_FEATURES = 2
private const val SHALLOW_DEPTH = 2

private fun format(value: Double): String = "%.4f".format(Locale.ROOT, value)

fun models(): List<Pair<String, Classifier>> =
    listOf(
        "決定木（深さ $SHALLOW_DEPTH）" to DecisionTreeClassifier(maxDepth = SHALLOW_DEPTH),
        "ロジスティック回帰" to LogisticRegression(),
        "ランダムフォレスト（$N_ESTIMATORS 本）" to RandomForest(N_ESTIMATORS, MAX_FEATURES, seed = SEED),
        "ランダムフォレスト（$N_ESTIMATORS 本・深さ $SHALLOW_DEPTH）" to RandomForest(N_ESTIMATORS, MAX_FEATURES, SHALLOW_DEPTH, SEED),
        "Tribuo ロジスティック回帰" to TribuoClassifier(LogisticRegressionTrainer()),
        "Tribuo ランダムフォレスト（$N_ESTIMATORS 本）" to tribuoRandomForest(N_ESTIMATORS, maxDepth = null, seed = SEED.toLong()),
    )

fun main() {
    // Tribuo が学習の経過を標準エラーに出すので、警告以上だけにする
    Logger.getLogger("org.tribuo").level = Level.WARNING
    val split = prepareIris(File(dataDir(), "iris.csv"), testSize = TEST_SIZE, seed = SEED)
    println("モデル\t訓練データ\tテストデータ")
    for ((name, model) in models()) {
        val score = evaluate(model, split)
        println("$name\t${format(score.train)}\t${format(score.test)}")
    }

    val forest = RandomForest(N_ESTIMATORS, MAX_FEATURES, seed = SEED).fit(split.xTrain, split.tTrain)
    println("\nランダムフォレスト（$N_ESTIMATORS 本）の特徴量の重要度:")
    for ((feature, value) in forestImportances(forest, split.xTrain, split.tTrain)) {
        println("$feature\t${format(value)}")
    }
}
