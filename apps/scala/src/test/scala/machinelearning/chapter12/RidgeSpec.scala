package machinelearning.chapter12

import java.util.Random
import machinelearning.chapter02.Features
import machinelearning.chapter07.{LinearRegression, Matrix}
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

/** テストで使う架空の行列と正解。 */
object Samples:
  /** 4 列のうち、最初の 2 列だけで正解が決まる人工データ */
  def sparseDataset(): (Matrix, Vector[Double]) =
    val random = Random(0)
    val rows = Vector.fill(50)(Vector.fill(4)(random.nextGaussian()))
    (Matrix(rows), rows.map(row => 3.0 * row(0) - 2.0 * row(1) + 0.1 * random.nextGaussian()))

  /** 3 列の乱数の行列と、その線形結合に雑音を足した正解 */
  def randomDataset(): (Matrix, Vector[Double]) =
    val random = Random(1)
    val rows = Vector.fill(30)(Vector.fill(3)(random.nextGaussian()))
    (
      Matrix(rows),
      rows.map(row => 1.5 * row(0) - 0.7 * row(1) + 2.0 * row(2) + random.nextGaussian())
    )

  /** 行列の各行を、列名つきの特徴量にする。 */
  def toFeatures(x: Matrix, columns: Vector[String]): Vector[Features] =
    x.rows.map(Features(columns, _))

class RidgeSpec extends AnyFunSuite:
  import MatrixOps.*

  private val x = Matrix(Vector(Vector(1.0), Vector(2.0), Vector(3.0)))
  private val t = Vector(2.0, 4.0, 6.0)

  test("alpha が 0 なら最小二乗法と同じ係数と切片になる") {
    val model = Ridge.fit(x, t, 0.0)

    assert(model.coefficients.head === 2.0 +- 1e-9)
    assert(model.intercept === 0.0 +- 1e-9)
  }

  test("特徴量が 2 つでも係数と切片を求める") {
    val x2 = Matrix(Vector(Vector(1.0, 1.0), Vector(2.0, 4.0), Vector(3.0, 9.0), Vector(4.0, 16.0)))
    // 2 列目は 1 列目の 2 乗。正解は x^2 + x + 1
    val t2 = Vector(3.0, 7.0, 13.0, 21.0)

    val model = Ridge.fit(x2, t2, 0.0)

    assert(model.coefficients.head === 1.0 +- 1e-6)
    assert(model.coefficients(1) === 1.0 +- 1e-6)
    assert(model.intercept === 1.0 +- 1e-6)
  }

  test("alpha を大きくすると係数の絶対値の合計が小さくなる") {
    val sums = Vector(0.0, 1.0, 10.0, 100.0).map(Ridge.fit(x, t, _).coefficientAbsSum)

    assert(sums === sums.sorted.reverse)
  }

  test("alpha が 0 なら第 7 章の線形回帰と同じ係数と切片になる") {
    val (xr, tr) = Samples.randomDataset()
    val columns = Vector("a", "b", "c")

    val model = Ridge.fit(xr, tr, 0.0)
    val linear = LinearRegression.fit(Samples.toFeatures(xr, columns), tr)

    columns.lazyZip(model.coefficients).foreach { (column, coefficient) =>
      assert(coefficient === linear.coefficient(column) +- 1e-8, column)
    }
    assert(model.intercept === linear.intercept +- 1e-8)
  }

  test("特徴量と正解の件数が違えばエラーになる") {
    assert(intercept[IllegalArgumentException](Ridge.fit(x, Vector(1.0), 0.0)).getMessage.nonEmpty)
  }

  test("係数と切片から予測値を計算する") {
    val model = RegularizedModel(Vector(2.0, -1.0), 3.0)

    assert(model.predict(Matrix(Vector(Vector(1.0, 1.0), Vector(2.0, 0.0)))) === Vector(4.0, 7.0))
  }

  test("特徴量の列数と係数の数が違えばエラーになる") {
    val model = RegularizedModel(Vector(2.0, -1.0), 3.0)

    assert(intercept[IllegalArgumentException](model.predict(x)).getMessage.nonEmpty)
  }

  test("同じ大きさの行列を成分ごとに足す") {
    val a = Matrix(Vector(Vector(1.0, 2.0), Vector(3.0, 4.0)))
    val b = Matrix(Vector(Vector(10.0, 20.0), Vector(30.0, 40.0)))

    assert(a + b === Matrix(Vector(Vector(11.0, 22.0), Vector(33.0, 44.0))))
  }

  test("大きさが違う行列は足せない") {
    val a = Matrix(Vector(Vector(1.0, 2.0)))

    assert(intercept[IllegalArgumentException](a + x).getMessage.nonEmpty)
  }

  test("数と行列の積はすべての成分を数倍する") {
    val a = Matrix(Vector(Vector(1.0, 2.0), Vector(3.0, 4.0)))

    assert(a * 2.0 === Matrix(Vector(Vector(2.0, 4.0), Vector(6.0, 8.0))))
  }

  test("単位行列は対角成分が 1 でほかが 0") {
    assert(MatrixOps.identity(2) === Matrix(Vector(Vector(1.0, 0.0), Vector(0.0, 1.0))))
  }

  test("足し算と数倍は元の行列を変えない") {
    val a = Matrix(Vector(Vector(1.0, 2.0), Vector(3.0, 4.0)))

    val _ = (a + a, a * 3.0)

    assert(a === Matrix(Vector(Vector(1.0, 2.0), Vector(3.0, 4.0))))
  }
