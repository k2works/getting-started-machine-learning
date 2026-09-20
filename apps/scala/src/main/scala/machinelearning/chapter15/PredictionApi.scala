package machinelearning.chapter15

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*
import org.http4s.circe.*
import org.http4s.dsl.io.*
import org.http4s.{HttpRoutes, Request, Response}

/** プレゼンテーション層。http4s の経路と、HTTP の状態コード。 ドメイン層とアプリケーション層を知り、インフラ層は知らない。
  */
object PredictionApi:

  /** 置き場を使う API の経路。 */
  def routes(service: PredictionService): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case request @ POST -> Root / "cinema" / "sales" =>
      predictWith(
        request,
        RequestValidation.parseMovie,
        service.predictSales,
        _.sales.asJson,
        "sales"
      )

    case request @ POST -> Root / "survived" =>
      predictWith(
        request,
        RequestValidation.parsePassenger,
        service.predictSurvival,
        _.survived.asJson,
        "survived"
      )

    case GET -> Root / "health" =>
      val models = service.health
      val status = if models.cinema && models.survived then "ok" else "degraded"
      Ok(
        Json.obj(
          "status" -> status.asJson,
          "models" -> Json.obj(
            "cinema" -> models.cinema.asJson,
            "survived" -> models.survived.asJson
          )
        )
      )

    // 知らない経路。ほかの言語版と同じく JSON で返す（.orNotFound の既定は平文）
    case _ =>
      NotFound(Json.obj("detail" -> "見つかりません".asJson))
  }

  /** 本文を検証し、正しければ予測する。不正なら 422、モデルが無ければ 503 を返す。 */
  private def predictWith[I, O](
      request: Request[IO],
      parse: String => RequestValidation.Validation[I],
      predict: I => Either[PredictionError, O],
      encode: O => Json,
      field: String
  ): IO[Response[IO]] =
    request.as[String].flatMap { body =>
      parse(body) match
        case Left(errors) =>
          UnprocessableContent(Json.obj("detail" -> errors.asJson))
        case Right(input) =>
          predict(input) match
            case Right(prediction) => Ok(Json.obj(field -> encode(prediction)))
            case Left(error) => ServiceUnavailable(Json.obj("detail" -> error.describe.asJson))
    }
