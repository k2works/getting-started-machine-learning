package chapter08

import chapter02.splitTrainTest
import dataset.dataDir
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import java.io.File
import java.util.Locale

private const val TEST_SIZE = 0.2
private const val SEED = 0
private const val MAX_DEPTH = 5

/** 学習済みのパイプラインの保存先（apps/kotlin/model/ は .gitignore の対象） */
val MODEL_FILE = File("model/survived.ser")

private val NEW_PASSENGERS: AnyFrame =
    dataFrameOf(
        "Pclass" to listOf(1, 3),
        "Sex" to listOf("female", "male"),
        "Age" to listOf(null, null),
        "SibSp" to listOf(0, 0),
        "Parch" to listOf(0, 0),
        "Fare" to listOf(50.0, 8.0),
        "Embarked" to listOf("C", "S"),
    )

private fun format(value: Double): String = "%.3f".format(Locale.ROOT, value)

fun main() = main(MODEL_FILE)

fun main(modelFile: File) {
    val df = loadSurvived(File(dataDir(), "Survived.csv"))
    val (x, t) = splitFeaturesAndTarget(df)
    val split = splitTrainTest(x, t, testSize = TEST_SIZE, seed = SEED)
    val counts = t.groupingBy { it }.eachCount()
    println("データ件数: ${df.rowsCount()}（生存 ${counts[1]}, 死亡 ${counts[0]}）")
    println("訓練データ: ${split.xTrain.rowsCount()} 件, テストデータ: ${split.xTest.rowsCount()} 件")

    val pipelines =
        ClassWeight.entries.associateWith { classWeight ->
            buildPipeline(maxDepth = MAX_DEPTH, classWeight = classWeight).fit(split.xTrain, split.tTrain)
        }
    for ((classWeight, pipeline) in pipelines) {
        val result = evaluate(pipeline, split)
        println(
            "classWeight=$classWeight: 訓練 ${format(result.trainAccuracy)}, テスト ${format(result.testAccuracy)}, " +
                "生存者 ${result.survivors} 人中 ${result.foundSurvivors} 人を発見",
        )
    }

    saveModel(pipelines.getValue(ClassWeight.BALANCED), modelFile)
    val predictions = loadModel(modelFile).predict(NEW_PASSENGERS)
    println("保存したモデル: ${modelFile.name}")
    println("架空の乗客の予測: $predictions")
}
