package machinelearning.chapter15

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.parser
import org.http4s.*
import org.http4s.implicits.*
import org.scalatest.funsuite.AnyFunSuite

class PredictionApiSpec extends AnyFunSuite:
  private val movieJson = """{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}"""
  private val passengerJson =
    """{"pclass": 1, "sex": "female", "age": null, "sib_sp": 0, "parch": 0, "fare": 50.0, "embarked": "C"}"""
  private val stub = Stubs.store(_ => 1200.0, _ => true)

  /** 置き場を使う API に要求を送り、状態コードと本文を返す。ネットワークは使わない。 */
  private def send(store: ModelStore, request: Request[IO]): (Status, Json) =
    val response =
      PredictionApi.routes(PredictionService(store)).orNotFound.run(request).unsafeRunSync()
    val body = response.as[String].unsafeRunSync()
    (response.status, parser.parse(body).getOrElse(Json.Null))

  private def post(store: ModelStore, path: String, body: String): (Status, Json) =
    send(store, Request[IO](Method.POST, Uri.unsafeFromString(path)).withEntity(body))

  private def get(store: ModelStore, path: String): (Status, Json) =
    send(store, Request[IO](Method.GET, Uri.unsafeFromString(path)))

  test("映画の特徴量を送ると予測した興行収入を返す") {
    val (status, body) = post(stub, "/cinema/sales", movieJson)

    assert(status === Status.Ok)
    assert(body === parser.parse("""{"sales":1200.0}""").toOption.get)
  }

  test("乗客の特徴量を送ると生存の予測を返す") {
    val (status, body) = post(stub, "/survived", passengerJson)

    assert(status === Status.Ok)
    assert(body === parser.parse("""{"survived":true}""").toOption.get)
  }

  test("特徴量が不正なら 422 と理由の一覧を返す") {
    val (status, body) =
      post(stub, "/cinema/sales", """{"sns1": -1, "sns2": 500, "actor": 3000, "original": 1}""")

    assert(status === Status.UnprocessableContent)
    assert(
      body.hcursor.downField("detail").as[Vector[String]].toOption.get.head === "sns1 は 0 以上にしてください"
    )
  }

  test("JSON として読めなければ 422 を返す") {
    val (status, _) = post(stub, "/cinema/sales", "{")

    assert(status === Status.UnprocessableContent)
  }

  test("モデルが無ければ 503 と、パスを含まない理由を返す") {
    val (status, body) = post(Stubs.empty, "/cinema/sales", movieJson)

    assert(status === Status.ServiceUnavailable)
    assert(body.hcursor.downField("detail").as[String].toOption.get === "学習済みモデル cinema が見つかりません")
  }

  test("すべてのモデルを読み込めれば ok を返す") {
    val (status, body) = get(stub, "/health")

    assert(status === Status.Ok)
    assert(body.hcursor.downField("status").as[String].toOption.get === "ok")
  }

  test("読み込めないモデルがあれば degraded を返す") {
    val (_, body) = get(Stubs.empty, "/health")

    assert(body.hcursor.downField("status").as[String].toOption.get === "degraded")
    assert(!body.hcursor.downField("models").downField("cinema").as[Boolean].toOption.get)
  }

  test("知らない経路は 404 を返す") {
    val (status, _) = get(stub, "/unknown")

    assert(status === Status.NotFound)
  }
