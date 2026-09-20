package machinelearning.chapter13

import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite
import org.tribuo.math.la.DenseMatrix

class TribuoEigenLearningSpec extends AnyFunSuite:
  private val symmetric = DenseMatrix.createDenseMatrix(Array(Array(2.0, 1.0), Array(1.0, 2.0)))

  private def rounded(values: Array[Double]): Vector[Double] =
    values.toVector.map(v => math.round(v * 1e9) / 1e9)

  test("対称行列の固有値を大きい順に返す") {
    val eigen = symmetric.eigenDecomposition().orElseThrow()

    assert(rounded(eigen.eigenvalues().toArray) === Vector(3.0, 1.0))
  }

  test("対角成分の並びに関係なく固有値を大きい順に並べ替える") {
    val diagonal = DenseMatrix.createDenseMatrix(
      Array(Array(1.0, 0.0, 0.0), Array(0.0, 5.0, 0.0), Array(0.0, 0.0, 3.0))
    )

    val eigen = diagonal.eigenDecomposition().orElseThrow()

    assert(rounded(eigen.eigenvalues().toArray) === Vector(5.0, 3.0, 1.0))
  }

  test("i 番目の固有ベクトルは行列を掛けても向きが変わらず i 番目の固有値倍になる") {
    val eigen = symmetric.eigenDecomposition().orElseThrow()
    val a = symmetric.toArray

    (0 until 2).foreach { i =>
      val v = eigen.getEigenVector(i).toArray
      val lambda = eigen.eigenvalues().get(i)
      (0 until 2).foreach { row =>
        assert(a(row)(0) * v(0) + a(row)(1) * v(1) === lambda * v(row) +- 1e-9)
      }
    }
  }

  test("対称でない行列は固有値分解できず空の Optional を返す") {
    val asymmetric = DenseMatrix.createDenseMatrix(Array(Array(2.0, 1.0), Array(0.0, 2.0)))

    assert(asymmetric.eigenDecomposition().isEmpty)
  }
