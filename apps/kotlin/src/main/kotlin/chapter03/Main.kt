package chapter03

import chapter01.accuracy
import chapter02.prepareIris
import dataset.dataDir
import java.io.File
import java.util.Locale

private const val TEST_SIZE = 0.3
private const val SEED = 0
private val MAX_DEPTHS = listOf(1, 2, 3, 4, 5, null)
private const val TREE_DEPTH_TO_SHOW = 2

private fun format(value: Double): String = "%.4f".format(Locale.ROOT, value)

fun main() {
    val split = prepareIris(File(dataDir(), "iris.csv"), testSize = TEST_SIZE, seed = SEED)
    println("深さ\t訓練データ\tテストデータ")
    for (maxDepth in MAX_DEPTHS) {
        val model = DecisionTree(maxDepth).fit(split.xTrain, split.tTrain)
        val train = accuracy(model.predict(split.xTrain), split.tTrain)
        val test = accuracy(model.predict(split.xTest), split.tTest)
        println("${maxDepth ?: "制限なし"}\t${format(train)}\t${format(test)}")
    }

    val shallow = DecisionTree(TREE_DEPTH_TO_SHOW).fit(split.xTrain, split.tTrain)
    shallow.tree?.let {
        println("\n深さ $TREE_DEPTH_TO_SHOW の決定木:")
        println(formatTree(it))
    }
}
