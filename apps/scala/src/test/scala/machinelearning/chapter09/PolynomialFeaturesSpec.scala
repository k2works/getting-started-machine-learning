package machinelearning.chapter09

import machinelearning.chapter02.Features
import org.scalatest.funsuite.AnyFunSuite

class PolynomialFeaturesSpec extends AnyFunSuite:
  test("1 列なら元の列と 2 乗の列を返す") {
    val features = PolynomialFeatures.expand(Vector(Samples.rm(2), Samples.rm(3)), Vector("RM"))

    assert(features.head.columns === Vector("RM", "RM^2"))
    assert(features.map(_.value("RM^2")) === Vector(4.0, 9.0))
  }

  test("2 列なら 2 乗の列と 2 つの列の積の列を加える") {
    val columns = Vector("RM", "LSTAT")
    val x = Vector(Features(columns, Vector(2, 5)), Features(columns, Vector(3, 7)))

    val features = PolynomialFeatures.expand(x, columns)

    assert(features.head.columns === Vector("RM", "LSTAT", "RM^2", "RM LSTAT", "LSTAT^2"))
    assert(features.map(_.value("RM LSTAT")) === Vector(10.0, 21.0))
    assert(features.map(_.value("LSTAT^2")) === Vector(25.0, 49.0))
  }

  test("3 列なら scikit-learn の PolynomialFeatures と同じ並びで 9 列を作る") {
    val columns = Vector("RM", "LSTAT", "PTRATIO")
    val x = Vector(Features(columns, Vector(5.5, 12, 18)))

    val features = PolynomialFeatures.expand(x, columns)

    assert(
      features.head.columns === Vector(
        "RM",
        "LSTAT",
        "PTRATIO",
        "RM^2",
        "RM LSTAT",
        "RM PTRATIO",
        "LSTAT^2",
        "LSTAT PTRATIO",
        "PTRATIO^2"
      )
    )
  }

  test("指定した列だけをその順に選ぶ") {
    val columns = Vector("RM", "LSTAT")
    val x = Vector(Features(columns, Vector(2, 5)))

    assert(
      PolynomialFeatures.select(x, Vector("LSTAT")) === Vector(
        Features(Vector("LSTAT"), Vector(5.0))
      )
    )
  }
