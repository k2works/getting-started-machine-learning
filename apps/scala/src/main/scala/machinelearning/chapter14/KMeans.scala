package machinelearning.chapter14

import machinelearning.chapter02.Preprocessing
import machinelearning.chapter07.Matrix
import scala.collection.immutable.SeqMap

/** K-means の結果。
  *
  * @param labels
  *   点ごとのクラスタ番号
  * @param centers
  *   クラスタの中心を 1 行に 1 つずつ並べた行列
  * @param sse
  *   誤差平方和
  */
case class KMeansResult(labels: Vector[Int], centers: Matrix, sse: Double)

/** K-means によるクラスタリング。点は `Vector[Double]`、点の集まりは `Vector[Vector[Double]]` で表す。 */
object KMeans:

  /** 更新の回数の既定の上限（scikit-learn の KMeans と同じ） */
  val DefaultMaxIterations = 300

  /** 初期中心を試す回数の既定値（scikit-learn の KMeans の n_init と同じ） */
  val DefaultNInit = 10

  /** 2 点間の距離の 2 乗。 */
  def squaredDistance(a: Vector[Double], b: Vector[Double]): Double =
    a.lazyZip(b).map((x, y) => (x - y) * (x - y)).sum

  /** 各点を、最も近い中心のクラスタ番号に割り当てる。 */
  def assignClusters(
      points: Vector[Vector[Double]],
      centers: Vector[Vector[Double]]
  ): Vector[Int] =
    points.map(point => centers.indices.minBy(k => squaredDistance(point, centers(k))))

  /** クラスタごとに、割り当てられた点の平均を新しい中心にする。点が 1 つも無いクラスタは中心を変えない。 */
  def updateCenters(
      points: Vector[Vector[Double]],
      labels: Vector[Int],
      previous: Vector[Vector[Double]]
  ): Vector[Vector[Double]] =
    val grouped = labels.zip(points).groupMap(_._1)(_._2)
    previous.indices.toVector.map { k =>
      grouped.get(k) match
        case None => previous(k)
        case Some(members) =>
          members.transpose.map(_.sum / members.size)
    }

  /** 各点と、所属するクラスタの中心との距離の 2 乗の合計（誤差平方和）。 */
  def sumOfSquaredErrors(
      points: Vector[Vector[Double]],
      labels: Vector[Int],
      centers: Vector[Vector[Double]]
  ): Double =
    points.lazyZip(labels).map((point, label) => squaredDistance(point, centers(label))).sum

  /** 中心が変わらなくなるか、更新の回数が maxIterations に達するまで、割り当てと中心の更新を繰り返す。 */
  def fit(
      points: Vector[Vector[Double]],
      initialCenters: Vector[Vector[Double]],
      maxIterations: Int = DefaultMaxIterations
  ): KMeansResult =
    val centers = converge(points, initialCenters, maxIterations)
    val labels = assignClusters(points, centers)
    KMeansResult(labels, Matrix(centers), sumOfSquaredErrors(points, labels, centers))

  @annotation.tailrec
  private def converge(
      points: Vector[Vector[Double]],
      centers: Vector[Vector[Double]],
      remaining: Int
  ): Vector[Vector[Double]] =
    if remaining <= 0 then centers
    else
      val next = updateCenters(points, assignClusters(points, centers), centers)
      if next == centers then centers else converge(points, next, remaining - 1)

  /** シード付きの乱数で点を並べ替え、先頭から nClusters 個を初期中心にする。 */
  def chooseInitialCenters(
      points: Vector[Vector[Double]],
      nClusters: Int,
      seed: Long
  ): Vector[Vector[Double]] =
    Preprocessing.shuffle(points, seed).take(nClusters)

  /** 初期中心の候補ごとにクラスタリングし、SSE が最小の結果を返す。 */
  def best(
      points: Vector[Vector[Double]],
      initialCenterCandidates: Vector[Vector[Vector[Double]]]
  ): KMeansResult =
    initialCenterCandidates.map(fit(points, _)).minBy(_.sse)

  /** シードを 1 ずつずらして初期中心を nInit 通り選び、SSE が最小の結果を返す。 */
  def fitWithRestarts(
      points: Vector[Vector[Double]],
      nClusters: Int,
      seed: Long,
      nInit: Int = DefaultNInit
  ): KMeansResult =
    best(
      points,
      (0 until nInit).toVector.map(i => chooseInitialCenters(points, nClusters, seed + i))
    )

  /** クラスタ数ごとに、初期中心を nInit 通り試した最小の SSE。 */
  def sseByClusterCount(
      points: Vector[Vector[Double]],
      clusterCounts: Vector[Int],
      seed: Long,
      nInit: Int = DefaultNInit
  ): SeqMap[Int, Double] =
    SeqMap.from(clusterCounts.map(n => n -> fitWithRestarts(points, n, seed, nInit).sse))
