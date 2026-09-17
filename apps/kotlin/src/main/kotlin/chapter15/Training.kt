package chapter15

import chapter02.splitTrainTest
import chapter07.fitLinearRegression
import chapter07.prepareCinema
import chapter08.ClassWeight
import chapter08.buildPipeline
import chapter08.loadSurvived
import chapter08.splitFeaturesAndTarget
import java.io.File

private const val TEST_SIZE = 0.2
private const val SEED = 0
private const val MAX_DEPTH = 5

/** 第 7・8 章と同じ条件でモデルを学習し、置き場に保存する */
fun trainAndSaveModels(
    dataDirectory: File,
    store: FileModelStore,
) {
    val cinema = prepareCinema(File(dataDirectory, "cinema.csv"), testSize = TEST_SIZE, seed = SEED)
    store.saveSalesModel(fitLinearRegression(cinema.xTrain, cinema.tTrain))

    val (x, t) = splitFeaturesAndTarget(loadSurvived(File(dataDirectory, "Survived.csv")))
    val survived = splitTrainTest(x, t, testSize = TEST_SIZE, seed = SEED)
    val pipeline = buildPipeline(maxDepth = MAX_DEPTH, classWeight = ClassWeight.BALANCED)
    store.saveSurvivalModel(pipeline.fit(survived.xTrain, survived.tTrain))
}
