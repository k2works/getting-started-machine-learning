package chapter10

import chapter01.accuracy
import chapter02.TrainTestSplit
import chapter03.DecisionTree
import org.jetbrains.kotlinx.dataframe.AnyFrame

interface Classifier {
    fun fit(
        x: AnyFrame,
        t: List<String>,
    ): Classifier

    fun predict(x: AnyFrame): List<String>
}

class DecisionTreeClassifier(
    maxDepth: Int? = null,
) : Classifier {
    private val tree = DecisionTree(maxDepth)

    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): DecisionTreeClassifier {
        tree.fit(x, t)
        return this
    }

    override fun predict(x: AnyFrame): List<String> = tree.predict(x)
}

data class Score(
    val train: Double,
    val test: Double,
)

fun evaluate(
    model: Classifier,
    split: TrainTestSplit<String>,
): Score {
    model.fit(split.xTrain, split.tTrain)
    return Score(
        train = accuracy(model.predict(split.xTrain), split.tTrain),
        test = accuracy(model.predict(split.xTest), split.tTest),
    )
}
