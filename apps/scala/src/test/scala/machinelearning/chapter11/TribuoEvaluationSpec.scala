package machinelearning.chapter11

import machinelearning.chapter02.Features
import machinelearning.chapter03.TribuoTrees
import machinelearning.chapter07.TribuoRegression
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite
import org.tribuo.classification.Label
import org.tribuo.classification.evaluation.LabelEvaluator
import org.tribuo.evaluation.KFoldSplitter
import org.tribuo.regression.evaluation.RegressionEvaluator
import org.tribuo.regression.slm.SLMTrainer

/** Tribuo の CART を、この章の Model として使うテスト用のアダプター。 */
case class TribuoTree(maxDepth: Int, model: Option[org.tribuo.Model[Label]] = None)
    extends Model[String]:
  override def fit(x: Vector[Features], t: Vector[String]): TribuoTree =
    copy(model = Some(TribuoTrees.train(x, t, maxDepth)))
  override def predict(x: Vector[Features]): Vector[String] =
    TribuoTrees.predict(model.getOrElse(throw IllegalStateException("学習していません")), x)

class TribuoEvaluationSpec extends AnyFunSuite:
  import Samples.*

  private val positive = Label("1")
  private val x = features(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0)
  private val t = Vector("0", "0", "1", "0", "0", "1", "1", "0", "1", "1")
  private val model = TribuoTrees.train(x, t, 1)
  private val predicted = TribuoTrees.predict(model, x)
  private val evaluation = LabelEvaluator().evaluate(model, TribuoTrees.toDataset(x, t))

  test("混同行列が Tribuo の評価器と一致する") {
    val cm = ConfusionMatrix.of(t, predicted, "1")

    val tribuo = evaluation.getConfusionMatrix
    assert(
      Vector(tribuo.tp(positive), tribuo.fp(positive), tribuo.fn(positive), tribuo.tn(positive))
        === Vector(cm.tp.toDouble, cm.fp.toDouble, cm.fn.toDouble, cm.tn.toDouble)
    )
  }

  test("正解率と適合率と再現率と F 値が Tribuo の評価器と一致する") {
    val cm = ConfusionMatrix.of(t, predicted, "1")

    assert(Metrics.accuracy(t, predicted) === evaluation.accuracy() +- 1e-12)
    assert(Metrics.precision(cm) === evaluation.precision(positive) +- 1e-12)
    assert(Metrics.recall(cm) === evaluation.recall(positive) +- 1e-12)
    assert(Metrics.f1Score(cm) === evaluation.f1(positive) +- 1e-12)
  }

  test("正例を 1 件も予測しなければ Tribuo の評価器も適合率と再現率と F 値を 0 にする") {
    // 深さ 0 の木は、訓練データの多数派の "0" だけを予測する
    val mostlyNegative = Vector.fill(9)("0") :+ "1"
    val neverPositive = TribuoTrees.train(x, mostlyNegative, 0)

    val zero = LabelEvaluator().evaluate(neverPositive, TribuoTrees.toDataset(x, t))

    assert(
      (zero.precision(positive), zero.recall(positive), zero.f1(positive)) === (0.0, 0.0, 0.0)
    )
  }

  test("MSE は Tribuo の RegressionEvaluator の RMSE の 2 乗と一致する") {
    val xr = features(1, 2, 3, 4, 5, 6)
    val tr = Vector(1.1, 2.3, 2.8, 4.4, 4.9, 6.2)
    val regression = TribuoRegression.train(SLMTrainer(true), xr, tr)

    val rmse = RegressionEvaluator()
      .evaluate(regression, TribuoRegression.toDataset(xr, tr))
      .rmse()
      .values()
      .iterator()
      .next()

    assert(
      Metrics.meanSquaredError(tr, TribuoRegression.predict(regression, xr)) === rmse * rmse +- 1e-9
    )
  }

  private def tribuoTestSizes(nSamples: Int, nSplits: Int): Vector[Int] =
    val xs = features((0 until nSamples).map(_.toDouble)*)
    val ts = (0 until nSamples).toVector.map(i => if i < nSamples / 2 then "0" else "1")
    val splitter = KFoldSplitter[Label](nSplits, 0L)
    val sizes = Vector.newBuilder[Int]
    splitter
      .split(TribuoTrees.toDataset(xs, ts), true)
      .forEachRemaining(fold => sizes += fold.test.size)
    sizes.result()

  test("分割ごとのテストデータの件数が Tribuo の KFoldSplitter と一致する") {
    Vector((10, 3), (7, 2), (11, 4)).foreach { (nSamples, nSplits) =>
      val mine = CrossValidation.kFold(nSamples, nSplits, 0).map(_.test.size)

      assert(mine === tribuoTestSizes(nSamples, nSplits), s"$nSamples 件を $nSplits 分割")
    }
  }

  test("同じ分割なら Tribuo の評価器で採点した正解率の平均と一致する") {
    val xs = features((1 to 20).map(_ * 0.05)*)
    val ts = (1 to 20).toVector.map(i => if i % 3 == 0 || i > 12 then "1" else "0")
    val folds = CrossValidation.kFold(20, 4, 0)

    val tribuo = folds.map { fold =>
      val trained = TribuoTrees.train(pick(xs, fold.train), pick(ts, fold.train), 1)
      val test = TribuoTrees.toDataset(pick(xs, fold.test), pick(ts, fold.test))
      LabelEvaluator().evaluate(trained, test).accuracy()
    }

    val mine =
      CrossValidation.crossValidate(() => TribuoTree(1), xs, ts, folds, Metrics.accuracy)

    assert(mine.sum / mine.size === tribuo.sum / tribuo.size +- 1e-12)
  }
