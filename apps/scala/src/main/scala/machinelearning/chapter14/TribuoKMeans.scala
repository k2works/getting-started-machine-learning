package machinelearning.chapter14

import java.util.Locale
import org.tribuo.MutableDataset
import org.tribuo.clustering.kmeans.{KMeansModel, KMeansTrainer}
import org.tribuo.clustering.{ClusterID, ClusteringFactory}
import org.tribuo.impl.ArrayExample
import org.tribuo.math.distance.L2Distance
import org.tribuo.provenance.SimpleDataSourceProvenance

/** Tribuo の KMeansTrainer でクラスタリングし、自作と同じ SSE で比べる。 */
object TribuoKMeans:
  private val MaxIterations = KMeans.DefaultMaxIterations
  private val Threads = 1
  private val factory = ClusteringFactory()

  // Tribuo は特徴量を名前の順に並べるので、列の順と名前の順が一致するように 0 埋めする
  private def featureNames(dimensions: Int): Array[String] =
    (0 until dimensions).toArray.map(j => String.format(Locale.ROOT, "x%02d", j))

  /** 点の集まりを、クラスタ番号の無い Tribuo のデータセットにする。 */
  def toDataset(points: Vector[Vector[Double]]): MutableDataset[ClusterID] =
    val dataset =
      MutableDataset[ClusterID](SimpleDataSourceProvenance("points", factory), factory)
    val names = featureNames(points.head.size)
    points.foreach { point =>
      dataset.add(
        ArrayExample[ClusterID](ClusteringFactory.UNASSIGNED_CLUSTER_ID, names, point.toArray)
      )
    }
    dataset

  /** k-means++ で初期中心を選んで学習する。 */
  def train(points: Vector[Vector[Double]], nClusters: Int, seed: Long): KMeansModel =
    KMeansTrainer(
      nClusters,
      MaxIterations,
      L2Distance(),
      KMeansTrainer.Initialisation.PLUSPLUS,
      Threads,
      seed
    ).train(toDataset(points))

  /** 学習したモデルの中心を、1 行に 1 つずつ並べる。 */
  def centers(model: KMeansModel): Vector[Vector[Double]] =
    model.getCentroidVectors.toVector.map(_.toArray.toVector)

  /** Tribuo で学習した中心に自作の割り当てをして、自作と同じ定義の SSE を求める。 */
  def sse(points: Vector[Vector[Double]], nClusters: Int, seed: Long): Double =
    val trained = centers(train(points, nClusters, seed))
    KMeans.sumOfSquaredErrors(points, KMeans.assignClusters(points, trained), trained)

  /** シードを 1 ずつずらして nInit 回学習し、最小の SSE を返す。 */
  def bestSse(points: Vector[Vector[Double]], nClusters: Int, seed: Long, nInit: Int): Double =
    (0 until nInit).map(i => sse(points, nClusters, seed + i)).min
