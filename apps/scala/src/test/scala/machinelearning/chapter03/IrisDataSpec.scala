package machinelearning.chapter03

import java.nio.file.{Files, Paths}
import machinelearning.chapter01.KinokoTakenoko
import machinelearning.chapter02.{Features, Preprocessing, TrainTestSplit}
import machinelearning.dataset.DataDir
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class IrisDataSpec extends AnyFunSuite:
  private val csvFile = Paths.get(DataDir.current(), "iris.csv")

  private def requireData(): org.scalatest.Assertion =
    assume(Files.exists(csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）")

  private def irisSplit(): TrainTestSplit[Features, String] =
    Preprocessing.prepareIris(csvFile, 0.3, 0)

  test("深さ 2 の決定木はテストデータの 45 件中 43 件を正しく分類する") {
    requireData(): Unit
    val split = irisSplit()

    val predictions =
      DecisionTree.withMaxDepth(2).fit(split.xTrain, split.tTrain).predict(split.xTest)

    assert(KinokoTakenoko.accuracy(predictions, split.tTest) === 43.0 / 45 +- 1e-12)
  }

  test("この分割ではどの深さでも Tribuo の CART とテストデータの予測が一致する") {
    requireData(): Unit
    val split = irisSplit()

    Vector(1, 2, 3, 4, 5, TribuoTrees.Unlimited).foreach { maxDepth =>
      val mine =
        (if maxDepth == TribuoTrees.Unlimited then DecisionTree.unlimited()
         else DecisionTree.withMaxDepth(maxDepth))
          .fit(split.xTrain, split.tTrain)
          .predict(split.xTest)
      val tribuo =
        TribuoTrees.predict(TribuoTrees.train(split.xTrain, split.tTrain, maxDepth), split.xTest)

      assert(mine === tribuo, s"深さ $maxDepth")
    }
  }

  test("実行すると深さごとの正解率と深さ 2 の決定木を表示する") {
    requireData(): Unit
    val output = Vector.newBuilder[String]

    Main.run(output += _)

    assert(
      output.result() === Vector(
        "深さ\t訓練データ\tテストデータ",
        "1\t0.6762\t0.6444",
        "2\t0.9333\t0.9556",
        "3\t0.9524\t0.9556",
        "4\t0.9619\t0.9556",
        "5\t0.9810\t0.9333",
        "制限なし\t1.0000\t0.9333",
        "",
        "深さ 2 の決定木:",
        Vector(
          "花弁幅 <= 0.2950",
          "  Iris-setosa",
          "花弁幅 > 0.2950",
          "  花弁幅 <= 0.6500",
          "    Iris-versicolor",
          "  花弁幅 > 0.6500",
          "    Iris-virginica"
        ).mkString("\n")
      )
    )
  }
