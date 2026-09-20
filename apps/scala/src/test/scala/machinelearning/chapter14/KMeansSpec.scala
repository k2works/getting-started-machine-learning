package machinelearning.chapter14

import machinelearning.chapter07.Matrix
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.immutable.SeqMap

class KMeansSpec extends AnyFunSuite:
  private def pointsOf(rows: Vector[Double]*): Vector[Vector[Double]] = rows.toVector

  private def twoGroups(): Vector[Vector[Double]] =
    pointsOf(Vector(0, 0), Vector(0, 1), Vector(10, 10), Vector(10, 11))

  private def threePairs(): Vector[Vector[Double]] =
    pointsOf(Vector(0), Vector(1), Vector(10), Vector(11), Vector(20), Vector(21))

  private def numberedPoints(size: Int): Vector[Vector[Double]] =
    (0 until size).toVector.map(i => Vector(i.toDouble, i * 2.0))

  test("各点を最も近い中心のクラスタに割り当てる") {
    val points = pointsOf(Vector(0.0), Vector(1.0), Vector(9.0), Vector(10.0))
    val centers = pointsOf(Vector(0.0), Vector(10.0))

    assert(KMeans.assignClusters(points, centers) === Vector(0, 0, 1, 1))
  }

  test("2 次元の点をユークリッド距離で最も近い中心に割り当てる") {
    val points = pointsOf(Vector(0, 0), Vector(5, 4), Vector(1, 0))
    val centers = pointsOf(Vector(5, 5), Vector(0, 0))

    assert(KMeans.assignClusters(points, centers) === Vector(1, 0, 1))
  }

  test("クラスタごとに割り当てられた点の平均を新しい中心にする") {
    val points = pointsOf(Vector(0, 0), Vector(2, 0), Vector(10, 10), Vector(10, 12))
    val previous = pointsOf(Vector(0, 0), Vector(0, 0))

    assert(
      KMeans.updateCenters(points, Vector(0, 0, 1, 1), previous) ===
        Vector(Vector(1, 0), Vector(10, 11))
    )
  }

  test("点が 1 つも割り当てられなかったクラスタは中心を変えない") {
    val points = pointsOf(Vector(0, 0), Vector(2, 4))
    val previous = pointsOf(Vector(0, 0), Vector(99, 99))

    assert(
      KMeans.updateCenters(points, Vector(0, 0), previous) === Vector(Vector(1, 2), Vector(99, 99))
    )
  }

  test("各点と所属するクラスタの中心との距離の 2 乗を合計する") {
    val points = pointsOf(Vector(0, 0), Vector(2, 0), Vector(10, 10), Vector(10, 12))
    val centers = pointsOf(Vector(1, 0), Vector(10, 11))

    assert(KMeans.sumOfSquaredErrors(points, Vector(0, 0, 1, 1), centers) === 4.0)
  }

  test("中心から離れた点ほど誤差が大きくなる") {
    assert(
      KMeans.sumOfSquaredErrors(
        Vector(Vector(0.0), Vector(4.0)),
        Vector(0, 0),
        Vector(Vector(1.0))
      ) === 10.0
    )
  }

  test("割り当てが変わらなくなるまで割り当てと中心の更新を繰り返す") {
    val result = KMeans.fit(twoGroups(), Vector(Vector(0, 0), Vector(0, 1)))

    assert(
      result === KMeansResult(
        Vector(0, 0, 1, 1),
        Matrix(Vector(Vector(0, 0.5), Vector(10, 10.5))),
        1.0
      )
    )
  }

  test("最大反復回数に達したら収束していなくても打ち切る") {
    val result = KMeans.fit(twoGroups(), Vector(Vector(0, 0), Vector(0, 1)), 1)

    assert(result.centers.rows.head === Vector(0.0, 0.0))
    assert(result.centers.rows(1).head === 20.0 / 3 +- 1e-9)
    assert(result.centers.rows(1)(1) === 22.0 / 3 +- 1e-9)
    assert(result.labels === Vector(0, 0, 1, 1))
  }

  test("データの中から重複なくクラスタ数だけ点を選ぶ") {
    val points = numberedPoints(10)

    val centers = KMeans.chooseInitialCenters(points, 3, 0)

    assert(centers.distinct.size === 3)
    assert(centers.forall(points.contains))
  }

  test("同じシードなら同じ点を選ぶ") {
    val points = numberedPoints(10)

    assert(
      KMeans.chooseInitialCenters(points, 3, 42) === KMeans.chooseInitialCenters(points, 3, 42)
    )
  }

  test("シードが違えば違う点を選ぶ") {
    val points = numberedPoints(10)

    assert(KMeans.chooseInitialCenters(points, 3, 0) !== KMeans.chooseInitialCenters(points, 3, 1))
  }

  test("クラスタ数ごとにクラスタリングしたときの SSE を求める") {
    assert(KMeans.sseByClusterCount(twoGroups(), Vector(1, 2), 0) === SeqMap(1 -> 201.0, 2 -> 1.0))
  }

  test("初期中心を変えて繰り返し最小の SSE を使う") {
    assert(KMeans.sseByClusterCount(threePairs(), Vector(3), 0, 10) === SeqMap(3 -> 1.5))
  }

  test("初期中心によっては局所解に陥る") {
    val stuck = KMeans.fit(threePairs(), Vector(Vector(0), Vector(1), Vector(10)))

    assert(stuck.sse === 101.0)
  }

  test("複数の初期中心の候補のうち SSE が最小の結果を返す") {
    val candidates = Vector(
      Vector(Vector(0.0), Vector(1.0), Vector(10.0)),
      Vector(Vector(0.0), Vector(10.0), Vector(20.0))
    )

    val result = KMeans.best(threePairs(), candidates)

    assert(result.sse === 1.5)
    assert(result.centers === Matrix(Vector(Vector(0.5), Vector(10.5), Vector(20.5))))
  }
