package chapter10

import chapter03.predictWithTribuo
import chapter03.toTribuoDataset
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.tribuo.Model
import org.tribuo.Trainer
import org.tribuo.classification.Label
import org.tribuo.classification.dtree.CARTClassificationTrainer
import org.tribuo.classification.ensemble.VotingCombiner
import org.tribuo.common.tree.RandomForestTrainer

class TribuoClassifier(
    private val trainer: Trainer<Label>,
) : Classifier {
    private var model: Model<Label>? = null

    override fun fit(
        x: AnyFrame,
        t: List<String>,
    ): TribuoClassifier {
        model = trainer.train(toTribuoDataset(x, t))
        return this
    }

    override fun predict(x: AnyFrame): List<String> {
        val fitted = checkNotNull(model) { "fit で学習してから predict を呼んでください" }
        return predictWithTribuo(fitted, x)
    }
}

// 分割ごとに半分の特徴量から選ぶ決定木を、ブートストラップ標本で nEstimators 本学習して多数決する
private const val FRACTION_FEATURES_IN_SPLIT = 0.5f

fun tribuoRandomForest(
    nEstimators: Int,
    maxDepth: Int?,
    seed: Long,
): TribuoClassifier {
    val tree = CARTClassificationTrainer(maxDepth ?: Int.MAX_VALUE, FRACTION_FEATURES_IN_SPLIT, seed)
    return TribuoClassifier(RandomForestTrainer(tree, VotingCombiner(), nEstimators, seed))
}
