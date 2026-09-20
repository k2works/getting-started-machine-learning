package machinelearning.chapter09

import machinelearning.chapter02.{Features, TrainTestSplit}
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class LinearModelSpec extends AnyFunSuite:
  private def price(rm: Double): Double = 3 * rm * rm + 1

  private def quadraticSplit(): TrainTestSplit[Features, Double] =
    val columns = Vector("RM", "LSTAT")
    val trainRm = Vector(1.0, 2.0, 3.0, 4.0)
    val trainLstat = Vector(9.0, 7.0, 8.0, 6.0)
    TrainTestSplit(
      trainRm.zip(trainLstat).map((rm, lstat) => Features(columns, Vector(rm, lstat))),
      Vector(Features(columns, Vector(5, 5)), Features(columns, Vector(6, 4))),
      trainRm.map(price),
      Vector(price(5), price(6))
    )

  test("正規方程式を解いて切片と係数を求める") {
    val rows = Vector(Vector(1.0, 0.0), Vector(0.0, 1.0), Vector(1.0, 1.0), Vector(2.0, 3.0))
    val t = rows.map(row => 2 + 3 * row(0) - row(1))

    val model = LinearModel.fit(rows, t)

    assert(model.intercept === 2.0 +- 1e-9)
    assert(model.weights(0) === 3.0 +- 1e-9)
    assert(model.weights(1) === -1.0 +- 1e-9)
  }

  test("予測がすべて当たれば決定係数は 1、平均を返すだけなら 0") {
    val actual = Vector(1.0, 2.0, 3.0)

    assert(LinearModel.rSquared(actual, actual) === 1.0 +- 1e-12)
    assert(LinearModel.rSquared(actual, Vector(2.0, 2.0, 2.0)) === 0.0 +- 1e-12)
  }

  test("列が互いに独立でなければ正規方程式を解けない") {
    val rows = Vector(Vector(1.0, 2.0), Vector(2.0, 4.0), Vector(3.0, 6.0))

    assertThrows[IllegalArgumentException] {
      LinearModel.fit(rows, Vector(1.0, 2.0, 3.0))
    }
  }

  test("2 乗の項が無いと 2 次式の価格を当てきれない") {
    val scores = Boston.scoreFeatureSet(quadraticSplit(), Vector("RM"), Vector("RM"))

    assert(scores.train < 1.0)
  }

  test("2 乗の項を加えると 2 次式の価格を当てられる") {
    val scores = Boston.scoreFeatureSet(quadraticSplit(), Vector("RM"), Vector("RM", "RM^2"))

    assert(scores.train === 1.0 +- 1e-9)
    assert(scores.test === 1.0 +- 1e-9)
  }
