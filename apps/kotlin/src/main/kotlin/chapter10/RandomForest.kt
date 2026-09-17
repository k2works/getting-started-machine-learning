package chapter10

import chapter03.DecisionTree
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.toDataFrame
import kotlin.random.Random

fun majorityVote(votes: List<List<String>>): List<String> =
    votes.first().indices.map { sample ->
        votes
            .map { it[sample] }
            .groupingBy { it }
            .eachCount()
            .maxBy { it.value }
            .key
    }

fun bootstrapSample(
    size: Int,
    random: Random,
): List<Int> = List(size) { random.nextInt(size) }

fun AnyFrame.selectColumns(names: List<String>): AnyFrame = names.map { this[it] }.toDataFrame()

data class FittedTree(
    val columns: List<String>,
    val rows: List<Int>,
    val model: DecisionTree,
)

class RandomForest(
    private val nEstimators: Int = 10,
    private val maxFeatures: Int = 2,
    private val maxDepth: Int? = null,
    private val seed: Int = 0,
) : Classifier {
    var trees: List<FittedTree> = emptyList()
        private set

    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): RandomForest {
        val random = Random(seed)
        trees =
            List(nEstimators) {
                val rows = bootstrapSample(x.rowsCount(), random)
                val chosen =
                    x
                        .columnNames()
                        .shuffled(random)
                        .take(maxFeatures)
                        .toSet()
                val columns = x.columnNames().filter { it in chosen }
                val model = DecisionTree(maxDepth).fit(x[rows].selectColumns(columns), t.slice(rows))
                FittedTree(columns = columns, rows = rows, model = model)
            }
        return this
    }

    override fun predict(x: AnyFrame): List<String> {
        check(trees.isNotEmpty()) { "fit で学習してから predict を呼んでください" }
        return majorityVote(trees.map { it.model.predict(x.selectColumns(it.columns)) })
    }
}
