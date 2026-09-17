package chapter15

import chapter07.LinearModel
import chapter08.FittedPipeline
import chapter08.loadModel
import chapter08.saveModel
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.toColumn
import java.io.File
import chapter07.FEATURES as CINEMA_FEATURES
import chapter08.FEATURES as SURVIVED_FEATURES

const val SALES_MODEL = "cinema"
const val SURVIVAL_MODEL = "survived"

/** 第 7 章の線形回帰モデルを、ドメインの SalesModel の約束に合わせるアダプター */
class LinearSalesModel(
    private val model: LinearModel,
) : SalesModel {
    override fun predictSales(movie: Movie): Double {
        val values = mapOf("SNS1" to movie.sns1, "SNS2" to movie.sns2, "actor" to movie.actor, "original" to movie.original.toDouble())
        return model.predict(oneRow(CINEMA_FEATURES, values)).first()
    }
}

/** 第 8 章の学習済みパイプラインを、ドメインの SurvivalModel の約束に合わせるアダプター */
class PipelineSurvivalModel(
    private val pipeline: FittedPipeline,
) : SurvivalModel {
    override fun survives(passenger: Passenger): Boolean {
        val values =
            mapOf(
                "Pclass" to passenger.pclass,
                "Sex" to passenger.sex,
                "Age" to passenger.age,
                "SibSp" to passenger.sibSp,
                "Parch" to passenger.parch,
                "Fare" to passenger.fare,
                "Embarked" to passenger.embarked,
            )
        return pipeline.predict(oneRow(SURVIVED_FEATURES, values)).first() == 1
    }
}

private fun oneRow(
    columns: List<String>,
    values: Map<String, Any?>,
): AnyFrame = dataFrameOf(columns.map { listOf(values.getValue(it)).toColumn(it) })

/** 線形回帰モデルを JSON で保存するための形 */
@Serializable
private data class LinearModelFile(
    val intercept: Double,
    val coefficients: Map<String, Double>,
)

/** 学習済みモデルをディレクトリのファイルに保存し、読み込む */
class FileModelStore(
    private val modelDir: File,
) : ModelStore {
    fun saveSalesModel(model: LinearModel) {
        modelDir.mkdirs()
        salesModelFile().writeText(Json.encodeToString(LinearModelFile(model.intercept, model.coefficients)))
    }

    fun saveSurvivalModel(pipeline: FittedPipeline) = saveModel(pipeline, survivalModelFile())

    override fun loadSalesModel(): Result<SalesModel> =
        load(SALES_MODEL, salesModelFile()) { file ->
            val saved = Json.decodeFromString<LinearModelFile>(file.readText())
            LinearSalesModel(LinearModel(saved.intercept, saved.coefficients))
        }

    override fun loadSurvivalModel(): Result<SurvivalModel> =
        load(SURVIVAL_MODEL, survivalModelFile()) { file -> PipelineSurvivalModel(loadModel(file)) }

    private fun salesModelFile() = File(modelDir, "$SALES_MODEL.json")

    private fun survivalModelFile() = File(modelDir, "$SURVIVAL_MODEL.ser")

    private fun <T> load(
        model: String,
        file: File,
        read: (File) -> T,
    ): Result<T> = if (file.exists()) runCatching { read(file) } else Result.failure(ModelNotFoundException(model))
}
