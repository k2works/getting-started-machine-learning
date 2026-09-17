package chapter08

import org.jetbrains.kotlinx.dataframe.AnyFrame
import java.io.File
import java.io.ObjectInputFilter
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

/** 前処理を順に fit・transform してから、モデルを学習する */
data class Pipeline(
    val transformers: List<Transformer>,
    val model: DecisionTreeClassifier,
) {
    fun fit(
        x: AnyFrame,
        t: List<Int>,
    ): FittedPipeline {
        val (fitted, prepared) =
            transformers.fold(emptyList<FittedTransformer>() to x) { (done, data), transformer ->
                val fittedTransformer = transformer.fit(data)
                (done + fittedTransformer) to fittedTransformer.transform(data)
            }
        return FittedPipeline(fitted, model.fit(prepared, t))
    }
}

/** 学習済みの前処理とモデル。予測するときは前処理の transform だけを使う */
data class FittedPipeline(
    val transformers: List<FittedTransformer>,
    val model: FittedDecisionTree,
) : Serializable {
    private companion object {
        private const val serialVersionUID: Long = 1
    }

    fun transform(x: AnyFrame): AnyFrame = transformers.fold(x) { data, transformer -> transformer.transform(data) }

    fun predict(x: AnyFrame): List<Int> = model.predict(transform(x))
}

fun buildPipeline(
    maxDepth: Int,
    classWeight: ClassWeight,
): Pipeline =
    Pipeline(
        transformers =
            listOf(
                GroupMedianImputer(column = "Age", by = listOf("Pclass", "Sex")),
                MostFrequentImputer(column = "Embarked"),
                DummyEncoder(columns = listOf("Sex", "Embarked")),
            ),
        model = DecisionTreeClassifier(maxDepth, classWeight),
    )

fun saveModel(
    pipeline: FittedPipeline,
    modelFile: File,
) {
    modelFile.parentFile?.mkdirs()
    ObjectOutputStream(modelFile.outputStream()).use { it.writeObject(pipeline) }
}

/** 保存したパイプラインの復元に必要なクラスだけを読み込み、それ以外のクラスが含まれていたら読み込みを止める */
private val MODEL_CLASSES: ObjectInputFilter =
    ObjectInputFilter.Config.createFilter(
        "chapter08.*;java.lang.*;java.util.*;kotlin.collections.*;!*",
    )

fun loadModel(modelFile: File): FittedPipeline =
    ObjectInputStream(modelFile.inputStream()).use {
        it.setObjectInputFilter(MODEL_CLASSES)
        it.readObject() as FittedPipeline
    }
