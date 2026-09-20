package machinelearning.chapter11

import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class MetricsSpec extends AnyFunSuite:
  private val actual = Vector("1", "1", "0", "0", "1")
  private val predicted = Vector("1", "0", "0", "1", "1")

  test("すべて正解なら真陽性と真陰性だけを数える") {
    assert(
      ConfusionMatrix.of(Vector("1", "0"), Vector("1", "0"), "1") === ConfusionMatrix(1, 0, 0, 1)
    )
  }

  test("外れた予測を偽陽性と偽陰性に分けて数える") {
    assert(ConfusionMatrix.of(actual, predicted, "1") === ConfusionMatrix(2, 1, 1, 1))
  }

  test("正例を入れ替えると真陽性と真陰性が入れ替わる") {
    assert(ConfusionMatrix.of(actual, predicted, "0") === ConfusionMatrix(1, 1, 1, 2))
  }

  test("正解と予測の件数が違えばエラーになる") {
    val thrown = intercept[IllegalArgumentException](ConfusionMatrix.of(actual, Vector("1"), "1"))

    assert(thrown.getMessage.contains("正解 5 件、予測 1 件"))
  }

  test("適合率は正例と予測したうち本当に正例だった割合") {
    assert(Metrics.precision(ConfusionMatrix(2, 1, 1, 1)) === 2.0 / 3.0 +- 1e-12)
  }

  test("再現率は本当の正例のうち正例と予測できた割合") {
    assert(Metrics.recall(ConfusionMatrix(2, 1, 1, 1)) === 2.0 / 3.0 +- 1e-12)
  }

  test("F 値は適合率と再現率の調和平均") {
    assert(Metrics.f1Score(ConfusionMatrix(3, 1, 3, 3)) === 0.6 +- 1e-12)
  }

  test("正例を 1 件も予測しなければ適合率も再現率も F 値も 0 になる") {
    val cm = ConfusionMatrix(0, 0, 2, 3)

    assert(
      (Metrics.precision(cm), Metrics.recall(cm), Metrics.f1Score(cm)) === (0.0, 0.0, 0.0)
    )
  }

  test("正解率は一致した割合") {
    assert(Metrics.accuracy(actual, predicted) === 0.6 +- 1e-12)
  }

  test("平均二乗誤差は誤差の 2 乗の平均") {
    assert(
      Metrics.meanSquaredError(Vector(1.0, 2.0, 3.0), Vector(1.0, 4.0, 6.0)) === 13.0 / 3.0 +- 1e-12
    )
  }

  test("平均二乗誤差は外れた予測に敏感で平均絶対誤差より大きく増える") {
    val t = Vector(1.0, 2.0, 3.0, 4.0)
    val small = Vector(1.5, 2.5, 3.5, 4.5)
    val one = Vector(1.0, 2.0, 3.0, 6.0)

    assert(Metrics.meanSquaredError(t, small) === 0.25 +- 1e-12)
    assert(Metrics.meanSquaredError(t, one) === 1.0 +- 1e-12)
  }

  test("混同行列の指標を評価関数に変える") {
    val metric = Metrics.classificationMetric(Metrics.recall, "1")

    assert(metric(actual, predicted) === 2.0 / 3.0 +- 1e-12)
  }
