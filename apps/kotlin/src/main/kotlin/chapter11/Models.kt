package chapter11

import chapter03.DecisionTree
import chapter07.LinearModel
import chapter07.fitLinearRegression
import org.jetbrains.kotlinx.dataframe.AnyFrame

class DecisionTreeModel(
    maxDepth: Int?,
) : Model<String> {
    private val tree = DecisionTree(maxDepth)

    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ) {
        tree.fit(x, t)
    }

    override fun predict(x: AnyFrame): List<String> = tree.predict(x)
}

class LinearRegressionModel : Model<Double> {
    private var model: LinearModel? = null

    override fun fit(
        x: AnyFrame,
        t: List<Double>,
    ) {
        model = fitLinearRegression(x, t)
    }

    override fun predict(x: AnyFrame): List<Double> {
        val fitted = checkNotNull(model) { "fit で学習してから predict を呼んでください" }
        return fitted.predict(x)
    }
}
