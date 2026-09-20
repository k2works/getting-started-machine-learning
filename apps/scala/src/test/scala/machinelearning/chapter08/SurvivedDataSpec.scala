package machinelearning.chapter08

import java.nio.file.{Files, Path, Paths}
import machinelearning.chapter02.{Features, Preprocessing, Row, Table, TrainTestSplit}
import machinelearning.chapter03.{DecisionTree, TribuoTrees}
import machinelearning.dataset.DataDir
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class SurvivedDataSpec extends AnyFunSuite:
  private val csvFile = Paths.get(DataDir.current(), "Survived.csv")

  private def requireData(): org.scalatest.Assertion =
    assume(Files.exists(csvFile), "学習データ Survived.csv が配置されていない（gulp data:setup）")

  private def survivedSplit(): TrainTestSplit[Row, Int] =
    val rows = Table.load(csvFile).rows
    Preprocessing.splitTrainTest(rows, SurvivedData.target(rows), 0.2, 0)

  private def fit(
      split: TrainTestSplit[Row, Int],
      maxDepth: Int,
      classWeight: ClassWeight
  ): FittedPipeline =
    Pipeline
      .build(Some(maxDepth), classWeight)
      .fit(SurvivedData.features(split.xTrain), split.tTrain)

  private def evaluate(
      split: TrainTestSplit[Row, Int],
      maxDepth: Int,
      classWeight: ClassWeight
  ): Evaluation =
    Evaluation.evaluate(fit(split, maxDepth, classWeight), split)

  private def withTempDirectory(body: Path => Any): Unit =
    val directory = Files.createTempDirectory("chapter08")
    try
      val _ = body(directory)
    finally Files.walk(directory).sorted(java.util.Comparator.reverseOrder()).forEach(Files.delete)

  test("実データの件数と欠損値の数を確認する") {
    requireData(): Unit
    val table = Table.load(csvFile)

    val missing = table.countMissing.toMap

    assert(table.rows.size === 891)
    assert(missing("Age") === 177)
    assert(missing("Cabin") === 687)
    assert(missing("Embarked") === 2)
  }

  test("深さ 2 では balanced にすると見つけられる生存者が 41 人から 68 人に増える") {
    requireData(): Unit
    val split = survivedSplit()

    assert(evaluate(split, 2, ClassWeight.Unweighted).foundSurvivors === 41)
    assert(evaluate(split, 2, ClassWeight.Balanced).foundSurvivors === 68)
  }

  test("深さ 5 では balanced にすると見つけられる生存者が 59 人から 65 人に増える") {
    requireData(): Unit
    val split = survivedSplit()

    val unweighted = evaluate(split, 5, ClassWeight.Unweighted)
    val balanced = evaluate(split, 5, ClassWeight.Balanced)

    assert(unweighted.foundSurvivors === 59)
    assert(balanced.foundSurvivors === 65)
    assert(balanced.testAccuracy === 0.804 +- 1e-3)
  }

  test("前処理後のテストデータ 179 件で Tribuo の CART と予測が違う件数") {
    requireData(): Unit
    val split = survivedSplit()
    val expected =
      Vector(1 -> 0, 2 -> 0, 3 -> 0, 4 -> 0, 5 -> 2, 6 -> 0, 7 -> 0, 8 -> 0, 9 -> 1, 10 -> 0)

    expected.foreach { (maxDepth, mismatches) =>
      val pipeline = fit(split, maxDepth, ClassWeight.Unweighted)
      val xTrain = pipeline.features(SurvivedData.features(split.xTrain))
      val xTest = pipeline.features(SurvivedData.features(split.xTest))
      val tribuo =
        TribuoTrees.predict(
          TribuoTrees.train(xTrain, split.tTrain.map(_.toString), maxDepth),
          xTest
        )
      val mine = pipeline.model.predict(xTest).map(_.toString)

      assert(
        mine.zip(tribuo).count((mine, tribuo) => mine != tribuo) === mismatches,
        s"深さ $maxDepth"
      )
    }
  }

  test("重み付けなしなら、前処理後のテストデータで第 3 章の決定木と予測が一致する") {
    requireData(): Unit
    val split = survivedSplit()

    (1 to 10).foreach { maxDepth =>
      val pipeline = fit(split, maxDepth, ClassWeight.Unweighted)
      val xTrain: Vector[Features] = pipeline.features(SurvivedData.features(split.xTrain))
      val xTest: Vector[Features] = pipeline.features(SurvivedData.features(split.xTest))
      val chapter03 =
        DecisionTree.withMaxDepth(maxDepth).fit(xTrain, split.tTrain.map(_.toString)).predict(xTest)

      assert(pipeline.model.predict(xTest).map(_.toString) === chapter03, s"深さ $maxDepth")
    }
  }

  test("実行すると評価結果を表示してモデルを保存する") {
    requireData(): Unit
    withTempDirectory { directory =>
      val modelFile = directory.resolve("survived.model")
      val output = Vector.newBuilder[String]

      Main.run(output += _, modelFile)

      assert(Files.exists(modelFile))
      assert(
        output.result() === Vector(
          "データ件数: 891（生存 342, 死亡 549）",
          "訓練データ: 712 件, テストデータ: 179 件",
          "classWeight=none: 訓練 0.854, テスト 0.799, 生存者 79 人中 59 人を発見",
          "classWeight=balanced: 訓練 0.848, テスト 0.804, 生存者 79 人中 65 人を発見",
          "保存したモデル: survived.model",
          "架空の乗客の予測: Vector(1, 0)"
        )
      )
    }
  }
