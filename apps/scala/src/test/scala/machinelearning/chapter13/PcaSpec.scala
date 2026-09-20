package machinelearning.chapter13

import java.util.Random
import machinelearning.chapter07.Matrix
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class PcaSpec extends AnyFunSuite:
  private val Tolerance = 1e-9

  private def assertMatrix(expected: Vector[Vector[Double]], actual: Matrix): Unit =
    assert(actual.rowCount === expected.size)
    assert(actual.columnCount === expected.head.size)
    expected.zip(actual.rows).foreach { (expectedRow, actualRow) =>
      expectedRow.zip(actualRow).foreach((e, a) => assert(a === e +- Tolerance))
    }

  test("2 列の分散と共分散を並べた行列を返す") {
    val x = Matrix(Vector(Vector(1, 2), Vector(3, 6), Vector(5, 10)))

    assertMatrix(Vector(Vector(4, 8), Vector(8, 16)), Pca.covarianceMatrix(x))
  }

  test("3 列でも各列の分散と 2 列ずつの共分散を並べる") {
    val x = Matrix(Vector(Vector(1, 2, 0), Vector(3, 6, 1), Vector(5, 10, 5)))

    assertMatrix(
      Vector(Vector(4, 8, 5), Vector(8, 16, 10), Vector(5, 10, 7)),
      Pca.covarianceMatrix(x)
    )
  }

  test("完全に相関する 2 列なら第 1 主成分だけで分散をすべて説明する") {
    val x = Matrix(Vector(Vector(1, 2), Vector(3, 6), Vector(5, 10)))

    val model = Pca.fit(x, 2)

    assertMatrix(
      Vector(Vector(1 / math.sqrt(5), 2 / math.sqrt(5))),
      Matrix(Vector(model.components.rows.head))
    )
    assert(model.explainedVarianceRatio.head === 1.0 +- Tolerance)
    assert(model.explainedVarianceRatio(1) === 0.0 +- Tolerance)
  }

  /** 2 つの隠れた変数を 4 列に混ぜ、小さなノイズを加えた 40 行の架空のデータ。 */
  private def mixedDataset(): Matrix =
    val random = Random(0)
    val mixing = Vector(Vector(2.0, 0.5), Vector(0.3, 1.0), Vector(1.0, -1.0), Vector(0.0, 0.2))
    Matrix((0 until 40).toVector.map { _ =>
      val base = Vector(random.nextGaussian(), random.nextGaussian())
      mixing.map(m => m(0) * base(0) + m(1) * base(1) + random.nextGaussian() * 0.1)
    })

  test("主成分は寄与率の大きい順に指定した数だけ並ぶ") {
    val ratios = Pca.fit(mixedDataset(), 3).explainedVarianceRatio

    assert(ratios.size === 3)
    assert(ratios === ratios.sorted(Ordering[Double].reverse))
  }

  test("主成分の向きは絶対値が最大の要素が正になるようにそろえる") {
    val x = Matrix(Vector(Vector(1, 2), Vector(3, 6), Vector(5, 10)))

    val model = Pca.fit(x, 2)

    assertMatrix(
      Vector(Vector(2 / math.sqrt(5), -1 / math.sqrt(5))),
      Matrix(Vector(model.components.rows(1)))
    )
  }

  test("絶対値が最大の要素が正になるように主成分の向きをそろえる") {
    val components = Matrix(Vector(Vector(0.6, -0.8), Vector(-0.8, 0.6)))

    assertMatrix(Vector(Vector(-0.6, 0.8), Vector(0.8, -0.6)), Pca.normalizeSigns(components))
  }

  test("主成分は長さ 1 で互いに直交する") {
    val model = Pca.fit(mixedDataset(), 3)

    val gram = model.components * model.components.transpose

    assertMatrix(Vector(Vector(1, 0, 0), Vector(0, 1, 0), Vector(0, 0, 1)), gram)
  }

  test("主成分は分散共分散行列に掛けると分散の倍数になる固有ベクトル") {
    val x = mixedDataset()

    val model = Pca.fit(x, 3)

    val covariance = Pca.covarianceMatrix(x)
    model.components.rows.zip(model.explainedVariance).foreach { (component, variance) =>
      val projected = covariance * Matrix.columnVector(component*)
      assertMatrix(component.map(v => Vector(v * variance)), projected)
    }
  }

  test("平均を引いてから主成分の向きに射影する") {
    val model =
      PcaModel(Vector(1.0, 2.0), Matrix(Vector(Vector(0.6, 0.8))), Vector(1.0), Vector(1.0))

    assertMatrix(
      Vector(Vector(1.4), Vector(0.0)),
      Pca.transform(model, Matrix(Vector(Vector(2, 3), Vector(1, 2))))
    )
  }

  test("累積寄与率がしきい値に届くまでの主成分の数を返す") {
    assert(Pca.componentsNeeded(Vector(0.5, 0.25, 0.25), 0.75) === 2)
  }

  test("しきい値を上げると必要な主成分の数が増える") {
    assert(Pca.componentsNeeded(Vector(0.5, 0.25, 0.25), 0.8) === 3)
  }

  test("係数の絶対値が大きい順に列名と係数を返す") {
    val component = Vector(0.1, -0.7, 0.5)

    assert(
      Pca.topLoadings(component, Vector("ZN", "DIS", "TAX"), 2) ===
        Vector(Loading("DIS", -0.7), Loading("TAX", 0.5))
    )
  }
