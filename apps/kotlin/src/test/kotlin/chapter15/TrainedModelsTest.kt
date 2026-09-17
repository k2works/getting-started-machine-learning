package chapter15

import dataset.dataDir
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Assumptions.assumeTrue
import support.captureStdout
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** 実データで 1 回だけ学習し、テストの間で使い回すモデルの置き場 */
private val trainedModelDir: File by lazy {
    createTempDirectory("model").toFile().also { trainAndSaveModels(dataDir(), FileModelStore(it)) }
}

private fun requireTrainingData() {
    assumeTrue(
        File(dataDir(), "cinema.csv").exists() && File(dataDir(), "Survived.csv").exists(),
        "学習データ cinema.csv・Survived.csv が配置されていない（gulp data:setup）",
    )
}

class TrainedModelsTest {
    @BeforeTest
    fun requireData() = requireTrainingData()

    private fun trainedApiTest(block: suspend (client: HttpClient) -> Unit) =
        testApplication {
            application { predictionModule(PredictionService(FileModelStore(trainedModelDir))) }
            block(createClient { install(ContentNegotiation) { json() } })
        }

    @Test
    fun `学習したモデルを保存するとヘルスチェックがokになる`() =
        trainedApiTest { client ->
            val response = client.get("/health").body<HealthResponse>()

            assertEquals("ok", response.status)
        }

    @Test
    fun `学習した線形回帰モデルで興行収入を予測する`() =
        trainedApiTest { client ->
            val response =
                client.post("/cinema/sales") {
                    contentType(ContentType.Application.Json)
                    setBody(MovieRequest(sns1 = 200.0, sns2 = 500.0, actor = 3000.0, original = 1))
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(7830.42, response.body<SalesResponse>().sales, absoluteTolerance = 0.01)
        }

    @Test
    fun `学習したパイプラインで1等客室の女性は生存と予測する`() =
        trainedApiTest { client ->
            val response =
                client.post("/survived") {
                    contentType(ContentType.Application.Json)
                    setBody(PassengerRequest(pclass = 1, sex = "female", sibSp = 0, parch = 0, fare = 50.0))
                }

            assertEquals(SurvivalResponse(survived = true), response.body<SurvivalResponse>())
        }

    @Test
    fun `学習したパイプラインで3等客室の男性は死亡と予測する`() =
        trainedApiTest { client ->
            val response =
                client.post("/survived") {
                    contentType(ContentType.Application.Json)
                    setBody(PassengerRequest(pclass = 3, sex = "male", sibSp = 0, parch = 0, fare = 8.0, embarked = "S"))
                }

            assertEquals(SurvivalResponse(survived = false), response.body<SurvivalResponse>())
        }
}

class TrainAndReportTest {
    @BeforeTest
    fun requireData() = requireTrainingData()

    @Test
    fun `学習するとモデルを保存して起動するURLを表示する`() {
        val modelDir = createTempDirectory("model").toFile()

        val output = captureStdout { trainAndReport(modelDir) }

        assertTrue(File(modelDir, "cinema.json").exists())
        assertTrue(File(modelDir, "survived.ser").exists())
        assertEquals(
            "学習済みモデルを保存しました: cinema.json, survived.ser\n" +
                "API を起動します: http://127.0.0.1:8015\n",
            output,
        )
    }
}
