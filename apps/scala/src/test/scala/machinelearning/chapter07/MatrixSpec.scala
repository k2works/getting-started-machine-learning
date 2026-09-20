package machinelearning.chapter07

import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class MatrixSpec extends AnyFunSuite:
  test("行列の積を求める") {
    val a = Matrix(Vector(Vector(1.0, 2.0), Vector(3.0, 4.0)))
    val b = Matrix(Vector(Vector(5.0, 6.0), Vector(7.0, 8.0)))

    assert(a * b === Matrix(Vector(Vector(19.0, 22.0), Vector(43.0, 50.0))))
  }

  test("行数と列数が違う行列の積を求める") {
    val a = Matrix(Vector(Vector(1.0, 2.0, 3.0), Vector(4.0, 5.0, 6.0)))
    val b = Matrix(Vector(Vector(1.0), Vector(0.0), Vector(2.0)))

    assert(a * b === Matrix(Vector(Vector(7.0), Vector(16.0))))
  }

  test("左の列数と右の行数が違えば積を求められない") {
    val a = Matrix(Vector(Vector(1.0, 2.0)))

    val thrown = intercept[IllegalArgumentException](a * a)

    assert(thrown.getMessage === "requirement failed: 左の行列の列数 2 と右の行列の行数 1 が違います")
  }

  test("行によって列数が違う値からは作れない") {
    val thrown =
      intercept[IllegalArgumentException](Matrix(Vector(Vector(1.0, 2.0), Vector(3.0))))

    assert(thrown.getMessage === "requirement failed: 行によって列数が違います")
  }

  test("列ベクトルは値を縦に並べた 1 列の行列になる") {
    assert(Matrix.columnVector(1.0, 2.0) === Matrix(Vector(Vector(1.0), Vector(2.0))))
  }

  test("行と列を入れ替える") {
    val a = Matrix(Vector(Vector(1.0, 2.0, 3.0), Vector(4.0, 5.0, 6.0)))

    assert(a.transpose === Matrix(Vector(Vector(1.0, 4.0), Vector(2.0, 5.0), Vector(3.0, 6.0))))
  }

  test("連立方程式の解を求める") {
    val a = Matrix(Vector(Vector(2.0, 1.0), Vector(1.0, 3.0)))

    val x = a.solve(Matrix.columnVector(3.0, 5.0)).column(0)

    assert(x(0) === 0.8 +- 1e-9)
    assert(x(1) === 1.4 +- 1e-9)
  }

  test("3 元の連立方程式の解を求める") {
    val a = Matrix(Vector(Vector(4.0, 1.0, 2.0), Vector(1.0, 3.0, 0.0), Vector(2.0, 0.0, 5.0)))
    val b = a * Matrix.columnVector(1.0, -2.0, 3.0)

    val x = a.solve(b).column(0)

    assert(
      x.lazyZip(Vector(1.0, -2.0, 3.0)).forall((actual, expected) => actual === expected +- 1e-9)
    )
  }

  test("対角成分が 0 でも行を入れ替えて解を求める") {
    val a = Matrix(Vector(Vector(0.0, 1.0), Vector(1.0, 0.0)))

    val x = a.solve(Matrix.columnVector(2.0, 3.0)).column(0)

    assert(x === Vector(3.0, 2.0))
  }
