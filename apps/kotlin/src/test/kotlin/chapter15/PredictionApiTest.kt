package chapter15

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

private val MOVIE_JSON =
    buildJsonObject {
        put("sns1", 200.0)
        put("sns2", 500.0)
        put("actor", 3000.0)
        put("original", 1)
    }

/** スタブの置き場を使うサービスで API を起動し、JSON を送受信するクライアントでテストする */
private fun apiTest(
    store: ModelStore,
    block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit,
) = testApplication {
    application { predictionModule(PredictionService(store)) }
    val client = createClient { install(ContentNegotiation) { json() } }
    block(client)
}

private val PASSENGER_JSON =
    buildJsonObject {
        put("pclass", 1)
        put("sex", "female")
        put("age", null)
        put("sib_sp", 0)
        put("parch", 0)
        put("fare", 50.0)
        put("embarked", "C")
    }

private fun JsonObject.with(
    field: String,
    value: JsonElement,
) = JsonObject(this + (field to value))

private suspend fun HttpClient.postJson(
    path: String,
    body: JsonObject,
): HttpResponse =
    post(path) {
        contentType(ContentType.Application.Json)
        setBody(body)
    }

class CinemaSalesApiTest {
    @Test
    fun `映画の特徴量を送ると予測した興行収入を返す`() =
        apiTest(StubModelStore()) { client ->
            val response = client.postJson("/cinema/sales", MOVIE_JSON)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(buildJsonObject { put("sales", 1200.0) }, response.body<JsonElement>())
        }

    @Test
    fun `特徴量が不正なら422を返す`() =
        apiTest(StubModelStore()) { client ->
            for ((field, value) in listOf("sns1" to JsonPrimitive(-1.0), "actor" to JsonPrimitive("多い"), "original" to JsonPrimitive(2))) {
                val response = client.postJson("/cinema/sales", MOVIE_JSON.with(field, value))

                assertEquals(HttpStatusCode.UnprocessableEntity, response.status, "$field=$value")
            }
        }

    @Test
    fun `モデルが無ければ503を返す`() =
        apiTest(EmptyModelStore()) { client ->
            val response = client.postJson("/cinema/sales", MOVIE_JSON)

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals(buildJsonObject { put("detail", "学習済みモデル cinema が見つかりません") }, response.body<JsonElement>())
        }
}

class SurvivedApiTest {
    @Test
    fun `乗客の特徴量を送ると生存の予測を返す`() =
        apiTest(StubModelStore()) { client ->
            val response = client.postJson("/survived", PASSENGER_JSON)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(buildJsonObject { put("survived", true) }, response.body<JsonElement>())
        }

    @Test
    fun `年齢と乗船港は省略できる`() =
        apiTest(StubModelStore()) { client ->
            val response = client.postJson("/survived", JsonObject(PASSENGER_JSON - "age" - "embarked"))

            assertEquals(HttpStatusCode.OK, response.status)
        }

    @Test
    fun `特徴量が不正なら422を返す`() =
        apiTest(StubModelStore()) { client ->
            val invalids =
                listOf(
                    "pclass" to JsonPrimitive(4),
                    "sex" to JsonPrimitive("unknown"),
                    "embarked" to JsonPrimitive("X"),
                    "fare" to JsonPrimitive(-5.0),
                )
            for ((field, value) in invalids) {
                val response = client.postJson("/survived", PASSENGER_JSON.with(field, value))

                assertEquals(HttpStatusCode.UnprocessableEntity, response.status, "$field=$value")
            }
        }

    @Test
    fun `モデルが無ければ503を返す`() =
        apiTest(EmptyModelStore()) { client ->
            val response = client.postJson("/survived", PASSENGER_JSON)

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertEquals(buildJsonObject { put("detail", "学習済みモデル survived が見つかりません") }, response.body<JsonElement>())
        }
}

class HealthApiTest {
    @Test
    fun `すべてのモデルを読み込めればokを返す`() =
        apiTest(StubModelStore()) { client ->
            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                buildJsonObject {
                    put("status", "ok")
                    put(
                        "models",
                        buildJsonObject {
                            put("cinema", true)
                            put("survived", true)
                        },
                    )
                },
                response.body<JsonElement>(),
            )
        }

    @Test
    fun `読み込めないモデルがあればdegradedを返す`() =
        apiTest(EmptyModelStore()) { client ->
            val response = client.get("/health")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                buildJsonObject {
                    put("status", "degraded")
                    put(
                        "models",
                        buildJsonObject {
                            put("cinema", false)
                            put("survived", false)
                        },
                    )
                },
                response.body<JsonElement>(),
            )
        }
}
