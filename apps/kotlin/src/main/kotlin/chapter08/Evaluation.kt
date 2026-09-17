package chapter08

import chapter02.TrainTestSplit

data class Evaluation(
    val trainAccuracy: Double,
    val testAccuracy: Double,
    val foundSurvivors: Int,
    val survivors: Int,
)

private fun accuracy(
    predictions: List<Int>,
    labels: List<Int>,
): Double = predictions.zip(labels).count { (p, t) -> p == t }.toDouble() / labels.size

fun evaluate(
    pipeline: FittedPipeline,
    split: TrainTestSplit<Int>,
): Evaluation {
    val predictions = pipeline.predict(split.xTest)
    return Evaluation(
        trainAccuracy = accuracy(pipeline.predict(split.xTrain), split.tTrain),
        testAccuracy = accuracy(predictions, split.tTest),
        foundSurvivors = predictions.zip(split.tTest).count { (p, t) -> p == 1 && t == 1 },
        survivors = split.tTest.count { it == 1 },
    )
}
