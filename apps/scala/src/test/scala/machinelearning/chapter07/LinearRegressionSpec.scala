package machinelearning.chapter07

import machinelearning.chapter02.Features
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

/** テスト用の特徴量を作る。 */
object Rows:
  /** 列名と、行ごとの値から特徴量のリストを作る。 */
  def apply(columns: Vector[String], values: Vector[Double]*): Vector[Features] =
    values.toVector.map(Features(columns, _))

class LinearRegressionSpec extends AnyFunSuite:
  private def assertModel(
      expected: LinearModel,
      actual: LinearModel
  ): org.scalatest.Assertion =
    assert(actual.intercept === expected.intercept +- 1e-9)
    assert(actual.coefficients.map(_._1) === expected.coefficients.map(_._1))
    assert(
      actual.coefficients
        .lazyZip(expected.coefficients)
        .forall((a, e) => a._2 === e._2 +- 1e-9),
      s"係数が違います: ${actual.coefficients}"
    )

  test("直線上の点から切片と係数を求める") {
    val x = Rows(Vector("x"), Vector(0.0), Vector(1.0), Vector(2.0), Vector(3.0))
    val t = Vector(1.0, 3.0, 5.0, 7.0)

    val model = LinearRegression.fit(x, t)

    assertModel(LinearModel.of(1.0, Vector("x"), Vector(2.0)), model)
  }

  test("複数の特徴量から切片と係数を求める") {
    val ab = Vector(
      Vector(0.0, 0.0),
      Vector(1.0, 0.0),
      Vector(0.0, 1.0),
      Vector(2.0, 1.0),
      Vector(1.0, 3.0)
    )
    val x = Rows(Vector("a", "b"), ab*)
    val t = ab.map(row => 3 * row(0) - 2 * row(1) + 5)

    val model = LinearRegression.fit(x, t)

    assertModel(LinearModel.of(5.0, Vector("a", "b"), Vector(3.0, -2.0)), model)
  }

  test("計画行列の先頭には 1 の列が入る") {
    val x = Rows(Vector("a", "b"), Vector(2.0, 3.0), Vector(4.0, 5.0))

    assert(
      LinearRegression.designMatrix(x) ===
        Matrix(Vector(Vector(1.0, 2.0, 3.0), Vector(1.0, 4.0, 5.0)))
    )
  }

class LinearModelSpec extends AnyFunSuite:
  private val model = LinearModel.of(1.0, Vector("a", "b"), Vector(2.0, -1.0))

  test("切片と係数から予測値を計算する") {
    val x = Rows(Vector("a", "b"), Vector(1.0, 4.0), Vector(3.0, 0.5))

    assert(model.predict(x) === Vector(-1.0, 6.5))
  }

  test("列の並び順が違っても列名で係数を対応させる") {
    val x = Rows(Vector("b", "a"), Vector(4.0, 1.0), Vector(0.5, 3.0))

    assert(model.predict(x) === Vector(-1.0, 6.5))
  }

  test("列名で係数を読む") {
    assert(model.coefficient("b") === -1.0)
  }

  test("列名と係数の数が違えばモデルを作れない") {
    val thrown =
      intercept[IllegalArgumentException](LinearModel.of(0.0, Vector("a"), Vector(1.0, 2.0)))

    assert(thrown.getMessage === "requirement failed: 列名と係数の数が違います")
  }
