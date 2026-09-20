package machinelearning.chapter15

import java.nio.file.{Files, Path, Paths}
import machinelearning.dataset.DataDir
import org.scalatest.funsuite.AnyFunSuite

class TrainedModelsSpec extends AnyFunSuite:
  private def requireData(): org.scalatest.Assertion =
    val data = DataDir.current()
    assume(
      Files.exists(Paths.get(data, "cinema.csv")) && Files.exists(Paths.get(data, "Survived.csv")),
      "学習データ cinema.csv・Survived.csv が配置されていない（gulp data:setup）"
    )

  private def trainedStore(directory: Path): FileModelStore =
    val store = FileModelStore(directory)
    Training.trainAndSaveModels(DataDir.current(), store)
    store

  private def withTempDirectory[A](block: Path => A): A =
    val directory = Files.createTempDirectory("ml-scala-model-")
    try block(directory)
    finally
      Files.walk(directory).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete(_))

  test("学習したモデルを保存するとヘルスチェックが ok になる") {
    requireData(): Unit

    withTempDirectory { directory =>
      assert(PredictionService(trainedStore(directory)).health === Health(true, true))
    }
  }

  test("学習した線形回帰モデルで興行収入を予測する") {
    requireData(): Unit

    withTempDirectory { directory =>
      val model = trainedStore(directory).loadSalesModel().toOption.get

      assert(model(Movie(200.0, 500.0, 3000.0, true)) > 0)
    }
  }

  test("学習したパイプラインで 1 等客室の女性は生存と予測する") {
    requireData(): Unit

    withTempDirectory { directory =>
      val model = trainedStore(directory).loadSurvivalModel().toOption.get

      assert(
        model(Passenger(Pclass.First, Sex.Female, Some(30.0), 0, 0, 80.0, Some(Embarked.Cherbourg)))
      )
    }
  }

  test("学習したパイプラインで 3 等客室の男性は死亡と予測する") {
    requireData(): Unit

    withTempDirectory { directory =>
      val model = trainedStore(directory).loadSurvivalModel().toOption.get

      assert(
        !model(Passenger(Pclass.Third, Sex.Male, Some(30.0), 0, 0, 8.0, Some(Embarked.Southampton)))
      )
    }
  }

  test("学習するとモデルを保存して起動する URL を表示する") {
    requireData(): Unit

    withTempDirectory { directory =>
      val output = Vector.newBuilder[String]

      Main.trainAndReport(output += _, directory)

      assert(
        output.result() === Vector(
          "学習済みモデルを保存しました: cinema.model, survived.model",
          "API を起動します: http://127.0.0.1:8015"
        )
      )
      assert(Files.exists(directory.resolve("cinema.model")))
      assert(Files.exists(directory.resolve("survived.model")))
    }
  }
