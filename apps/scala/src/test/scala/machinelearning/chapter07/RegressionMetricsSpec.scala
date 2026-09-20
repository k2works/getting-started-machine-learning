package machinelearning.chapter07

import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class RegressionMetricsSpec extends AnyFunSuite:
  private val t = Vector(3.0, 5.0, 7.0)

  test("MAE は誤差の絶対値の平均になる") {
    assert(RegressionMetrics.meanAbsoluteError(t, Vector(2.0, 5.0, 9.0)) === 1.0 +- 1e-12)
  }

  test("MAE は予測が大きく外れるほど大きくなる") {
    assert(RegressionMetrics.meanAbsoluteError(t, Vector(1.0, 8.0, 7.0)) === 5.0 / 3 +- 1e-12)
  }

  test("実測値と予測値の件数が違えばエラーになる") {
    val thrown =
      intercept[IllegalArgumentException](RegressionMetrics.meanAbsoluteError(t, Vector(1.0)))

    assert(thrown.getMessage === "requirement failed: 実測値と予測値の件数が違います")
  }

  test("RMSE は誤差の 2 乗の平均の平方根になる") {
    assert(
      RegressionMetrics.rootMeanSquaredError(t, Vector(2.0, 5.0, 9.0)) ===
        math.sqrt(5.0 / 3) +- 1e-12
    )
  }

  test("R² は予測がすべて正解なら 1 になる") {
    assert(RegressionMetrics.r2Score(t, t) === 1.0 +- 1e-12)
  }

  test("R² は平均値を予測し続けるモデルより良い分だけ 1 に近づく") {
    assert(RegressionMetrics.r2Score(t, Vector(2.0, 5.0, 9.0)) === 1 - 5.0 / 8 +- 1e-12)
  }
