package machinelearning.chapter15

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.{ipv4, port}
import java.nio.file.{Path, Paths}
import machinelearning.dataset.DataDir
import org.http4s.ember.server.EmberServerBuilder

/** モデルを学習して保存し、予測 API を起動する。 */
object Main:
  /** 学習済みモデルの保存先（apps/scala/model/ は .gitignore の対象） */
  val ModelDirectory: Path = Paths.get("model")

  /** API が待ち受けるポート */
  val Port = 8015

  def run(print: String => Unit): Unit = trainAndReport(print, ModelDirectory)

  /** 保存先を指定して学習し、結果を表示する。テストから一時ディレクトリを渡すために使う。 */
  def trainAndReport(print: String => Unit, modelDirectory: Path): Unit =
    Training.trainAndSaveModels(DataDir.current(), FileModelStore(modelDirectory))
    print(
      s"学習済みモデルを保存しました: ${FileModelStore.SalesModelName}.model, ${FileModelStore.SurvivalModelName}.model"
    )
    print(s"API を起動します: http://127.0.0.1:$Port")

/** 学習してから API を起動する（手元で試すとき用）。sbt "runMain machinelearning.chapter15.Server" */
object Server extends IOApp.Simple:
  def run: IO[Unit] =
    IO(Main.trainAndReport(println, Main.ModelDirectory)) *>
      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"8015")
        .withHttpApp(
          PredictionApi.routes(PredictionService(FileModelStore(Main.ModelDirectory))).orNotFound
        )
        .build
        .useForever
