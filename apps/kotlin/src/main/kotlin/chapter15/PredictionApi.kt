package chapter15

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private val ORIGINAL_VALUES = setOf(0, 1)
private val PASSENGER_CLASSES = setOf(1, 2, 3)
private val SEXES = setOf("male", "female")
private val PORTS = setOf("C", "Q", "S")

/** 入力の検証結果。正しければ値を、不正なら理由の一覧を持つ */
sealed interface Validated<out T> {
    data class Valid<T>(
        val value: T,
    ) : Validated<T>

    data class Invalid(
        val errors: List<String>,
    ) : Validated<Nothing>
}

private fun <T> validated(
    errors: List<String?>,
    value: () -> T,
): Validated<T> = errors.filterNotNull().let { if (it.isEmpty()) Validated.Valid(value()) else Validated.Invalid(it) }

private fun notNegative(
    field: String,
    value: Double?,
): String? = if (value != null && value < 0) "$field は 0 以上にしてください" else null

private fun <T> oneOf(
    field: String,
    value: T?,
    allowed: Set<T>,
): String? = if (value != null && value !in allowed) "$field は ${allowed.joinToString("、")} のどれかにしてください" else null

@Serializable
data class MovieRequest(
    val sns1: Double,
    val sns2: Double,
    val actor: Double,
    val original: Int,
) {
    fun validate(): Validated<Movie> =
        validated(
            listOf(
                notNegative("sns1", sns1),
                notNegative("sns2", sns2),
                notNegative("actor", actor),
                oneOf("original", original, ORIGINAL_VALUES),
            ),
        ) { Movie(sns1, sns2, actor, original) }
}

@Serializable
data class PassengerRequest(
    val pclass: Int,
    val sex: String,
    val age: Double? = null,
    @SerialName("sib_sp") val sibSp: Int,
    val parch: Int,
    val fare: Double,
    val embarked: String? = null,
) {
    fun validate(): Validated<Passenger> =
        validated(
            listOf(
                oneOf("pclass", pclass, PASSENGER_CLASSES),
                oneOf("sex", sex, SEXES),
                notNegative("age", age),
                notNegative("sib_sp", sibSp.toDouble()),
                notNegative("parch", parch.toDouble()),
                notNegative("fare", fare),
                oneOf("embarked", embarked, PORTS),
            ),
        ) { Passenger(pclass, sex, age, sibSp, parch, fare, embarked) }
}

@Serializable
data class SalesResponse(
    val sales: Double,
)

@Serializable
data class SurvivalResponse(
    val survived: Boolean,
)

@Serializable
data class HealthResponse(
    val status: String,
    val models: Map<String, Boolean>,
)

@Serializable
data class ErrorResponse(
    val detail: String,
)

@Serializable
data class ValidationErrorResponse(
    val detail: List<String>,
)

fun Application.predictionModule(service: PredictionService) {
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        // JSON として読めない・型が合わない入力。例外のメッセージには内部の型名が含まれるので、応答には出さない
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.UnprocessableEntity, ValidationErrorResponse(listOf("JSON の形式または値の型が正しくありません")))
        }
        // ドメインの例外を HTTP のステータスコードに変換する
        exception<ModelNotFoundException> { call, cause ->
            call.respond(HttpStatusCode.ServiceUnavailable, ErrorResponse(cause.message.orEmpty()))
        }
    }
    routing {
        get("/health") {
            val models = service.health()
            call.respond(HealthResponse(status = if (models.values.all { it }) "ok" else "degraded", models = models))
        }
        post("/cinema/sales") {
            when (val movie = call.receive<MovieRequest>().validate()) {
                is Validated.Invalid -> call.respond(HttpStatusCode.UnprocessableEntity, ValidationErrorResponse(movie.errors))
                is Validated.Valid -> call.respond(SalesResponse(service.predictSales(movie.value).getOrThrow().sales))
            }
        }
        post("/survived") {
            when (val passenger = call.receive<PassengerRequest>().validate()) {
                is Validated.Invalid -> call.respond(HttpStatusCode.UnprocessableEntity, ValidationErrorResponse(passenger.errors))
                is Validated.Valid -> call.respond(SurvivalResponse(service.predictSurvival(passenger.value).getOrThrow().survived))
            }
        }
    }
}
