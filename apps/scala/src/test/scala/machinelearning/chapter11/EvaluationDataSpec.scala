package machinelearning.chapter11

import java.nio.file.{Files, Paths}
import machinelearning.chapter02.Table
import machinelearning.chapter03.TribuoTrees
import machinelearning.dataset.DataDir
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite
import org.tribuo.classification.Label
import org.tribuo.classification.evaluation.{LabelEvaluation, LabelEvaluator}

class EvaluationDataSpec extends AnyFunSuite:
  import Samples.pick

  private val survived = Paths.get(DataDir.current(), "Survived.csv")
  private val cinema = Paths.get(DataDir.current(), "cinema.csv")

  private def requireData(): org.scalatest.Assertion =
    assume(
      Files.exists(survived) && Files.exists(cinema),
      "学習データ Survived.csv・cinema.csv が配置されていない（gulp data:setup）"
    )

  test("Survived の特徴量は 3 列で、年齢の欠損値が平均値で補われる") {
    requireData(): Unit
    val table = Table.load(survived)

    val data = Dataset.prepareSurvived(table)

    assert(data.x.size === table.rows.size)
    assert(data.x.head.columns === Dataset.SurvivedFeatures)
    assert(data.t.toSet === Set("0", "1"))
  }

  test("同じ分割なら Tribuo の評価器で採点した Survived の平均と一致する") {
    requireData(): Unit
    val data = Dataset.prepareSurvived(Table.load(survived))
    val folds = CrossValidation.kFold(data.x.size, Experiments.NSplits, Experiments.Seed)
    val positive = Label("1")

    val evaluations = folds.map { fold =>
      val model = TribuoTrees.train(pick(data.x, fold.train), pick(data.t, fold.train), 2)
      val test = TribuoTrees.toDataset(pick(data.x, fold.test), pick(data.t, fold.test))
      LabelEvaluator().evaluate(model, test)
    }
    def mean(score: LabelEvaluation => Double): Double =
      evaluations.map(score).sum / evaluations.size

    val scores = Experiments.evaluate(() => TribuoTree(2), data, Experiments.SurvivedMetrics).toMap

    assert(scores("正解率") === mean(_.accuracy()) +- 1e-12)
    assert(scores("適合率") === mean(_.precision(positive)) +- 1e-12)
    assert(scores("再現率") === mean(_.recall(positive)) +- 1e-12)
    assert(scores("F値") === mean(_.f1(positive)) +- 1e-12)
  }

  test("実行すると交差検証の平均を表示する") {
    requireData(): Unit
    val output = Vector.newBuilder[String]

    Main.run(output += _)

    assert(
      output.result() === Vector(
        "Survived（決定木、5 分割交差検証の平均）",
        "  正解率: 0.7811",
        "  適合率: 0.7759",
        "  再現率: 0.6306",
        "  F値: 0.6833",
        "cinema（線形回帰、5 分割交差検証の平均）",
        "  RMSE: 405.77",
        "  MAE: 321.53"
      )
    )
  }
