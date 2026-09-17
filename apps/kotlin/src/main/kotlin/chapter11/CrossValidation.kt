package chapter11

import org.jetbrains.kotlinx.dataframe.AnyFrame
import kotlin.random.Random

data class Fold(
    val train: List<Int>,
    val test: List<Int>,
)

fun kFold(
    nSamples: Int,
    nSplits: Int,
    seed: Int,
): List<Fold> {
    val positions = (0 until nSamples).shuffled(Random(seed))
    val sizes = List(nSplits) { i -> nSamples / nSplits + if (i < nSamples % nSplits) 1 else 0 }
    val tests = sizes.runningFold(0, Int::plus).zipWithNext { from, to -> positions.subList(from, to) }
    return tests.map { test -> Fold(train = positions - test.toSet(), test = test) }
}

interface Model<T> {
    fun fit(
        x: AnyFrame,
        t: List<T>,
    )

    fun predict(x: AnyFrame): List<T>
}

fun <T> crossValidate(
    makeModel: () -> Model<T>,
    x: AnyFrame,
    t: List<T>,
    folds: List<Fold>,
    metric: Metric<T>,
): Sequence<Double> =
    folds.asSequence().map { fold ->
        val model = makeModel()
        model.fit(x[fold.train], t.slice(fold.train))
        metric(t.slice(fold.test), model.predict(x[fold.test]))
    }
