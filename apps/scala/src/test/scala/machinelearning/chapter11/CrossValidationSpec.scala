package machinelearning.chapter11

import machinelearning.chapter02.Features
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

/** テストで使う架空の特徴量。 */
object Samples:
  /** 1 列（x）の特徴量を、値の並びから作る。 */
  def features(values: Double*): Vector[Features] =
    values.toVector.map(value => Features(Vector("x"), Vector(value)))

  /** 位置で選ぶ。 */
  def pick[A](values: Vector[A], positions: Vector[Int]): Vector[A] = positions.map(values)

class CrossValidationSpec extends AnyFunSuite:
  import Samples.*

  test("割り切れる件数は同じ大きさのテストデータに分ける") {
    val folds = CrossValidation.kFold(10, 5, 0)

    assert(folds.map(_.test.size) === Vector(2, 2, 2, 2, 2))
  }

  test("割り切れない件数は余りを先頭の分割から 1 件ずつ配る") {
    val folds = CrossValidation.kFold(11, 4, 0)

    assert(folds.map(_.test.size) === Vector(3, 3, 3, 2))
  }

  test("テストデータは重ならず全体を覆う") {
    val folds = CrossValidation.kFold(10, 3, 0)

    assert(folds.flatMap(_.test).sorted === (0 until 10).toVector)
  }

  test("訓練データはテストデータ以外のすべての行") {
    val folds = CrossValidation.kFold(10, 3, 0)

    folds.foreach { fold =>
      assert(fold.train.size === 10 - fold.test.size)
      assert(fold.train.toSet.intersect(fold.test.toSet).isEmpty)
    }
  }

  test("同じシードなら同じ分け方になる") {
    assert(CrossValidation.kFold(10, 5, 0) === CrossValidation.kFold(10, 5, 0))
  }

  test("シードが違えば分け方が変わる") {
    assert(CrossValidation.kFold(10, 5, 0) !== CrossValidation.kFold(10, 5, 1))
  }

  test("件数より多い分割はできない") {
    assert(intercept[IllegalArgumentException](CrossValidation.kFold(3, 4, 0)).getMessage.nonEmpty)
  }

  test("分割ごとに学習し直して評価関数で採点する") {
    val x = features(0.1, 0.2, 0.3, 0.4, 0.5, 0.6)
    val t = Vector("0", "0", "0", "1", "1", "1")
    val folds = CrossValidation.kFold(6, 3, 0)

    val scores =
      CrossValidation.crossValidate(() => DecisionTreeModel(1), x, t, folds, Metrics.accuracy)

    assert(scores.size === 3)
    assert(scores.forall(score => score >= 0.0 && score <= 1.0))
  }

  test("評価関数を差し替えると同じ分割で別のスコアになる") {
    val x = features(0.1, 0.2, 0.3, 0.4, 0.5, 0.6)
    val t = Vector("0", "1", "0", "1", "1", "1")
    val folds = CrossValidation.kFold(6, 3, 0)
    def run(metric: Metric[String]) =
      CrossValidation.crossValidate(() => DecisionTreeModel(1), x, t, folds, metric).toVector

    val accuracy = run(Metrics.accuracy)
    val recall = run(Metrics.classificationMetric(Metrics.recall, "1"))

    assert(accuracy !== recall)
  }

  test("スコアは遅延評価で、取り出した分だけ学習する") {
    var fitted = 0
    val counting = new Model[String]:
      override def fit(x: Vector[Features], t: Vector[String]): Model[String] =
        fitted += 1
        this
      override def predict(x: Vector[Features]): Vector[String] = x.map(_ => "0")
    val x = features(0.1, 0.2, 0.3, 0.4, 0.5, 0.6)
    val t = Vector("0", "0", "0", "1", "1", "1")

    val scores =
      CrossValidation.crossValidate(
        () => counting,
        x,
        t,
        CrossValidation.kFold(6, 3, 0),
        Metrics.accuracy
      )
    val first = scores.head

    assert(fitted === 1)
    assert(first >= 0.0)
    assert(scores.toVector.size === 3)
    assert(fitted === 3)
  }

  test("回帰でも同じ交差検証の手順を使える") {
    val x = features(1, 2, 3, 4, 5, 6)
    val t = Vector(2.0, 4.0, 6.0, 8.0, 10.0, 12.0)

    val scores = CrossValidation.crossValidate(
      () => LinearRegressionModel(),
      x,
      t,
      CrossValidation.kFold(6, 3, 0),
      Metrics.meanSquaredError
    )

    assert(scores.sum / scores.size === 0.0 +- 1e-12)
  }
