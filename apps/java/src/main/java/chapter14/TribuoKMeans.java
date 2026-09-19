package chapter14;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.LongStream;
import org.tribuo.MutableDataset;
import org.tribuo.clustering.ClusterID;
import org.tribuo.clustering.ClusteringFactory;
import org.tribuo.clustering.kmeans.KMeansModel;
import org.tribuo.clustering.kmeans.KMeansTrainer;
import org.tribuo.impl.ArrayExample;
import org.tribuo.math.distance.L2Distance;
import org.tribuo.math.la.DenseVector;
import org.tribuo.provenance.SimpleDataSourceProvenance;

/** Tribuo の KMeansTrainer でクラスタリングし、自作と同じ SSE で比べる。 */
public final class TribuoKMeans {
  private static final int MAX_ITERATIONS = KMeans.DEFAULT_MAX_ITERATIONS;
  private static final int THREADS = 1;
  private static final ClusteringFactory FACTORY = new ClusteringFactory();

  private TribuoKMeans() {}

  // Tribuo は特徴量を名前の順に並べるので、列の順と名前の順が一致するように 0 埋めする
  private static String[] featureNames(int dimensions) {
    String[] names = new String[dimensions];
    for (int j = 0; j < dimensions; j++) {
      names[j] = String.format(Locale.ROOT, "x%02d", j);
    }
    return names;
  }

  /** 点の配列を、クラスタ番号の無い Tribuo のデータセットにする。 */
  public static MutableDataset<ClusterID> toDataset(double[][] points) {
    var dataset = new MutableDataset<>(new SimpleDataSourceProvenance("points", FACTORY), FACTORY);
    String[] names = featureNames(points[0].length);
    for (double[] point : points) {
      dataset.add(new ArrayExample<>(ClusteringFactory.UNASSIGNED_CLUSTER_ID, names, point));
    }
    return dataset;
  }

  /** k-means++ で初期中心を選んで学習する。 */
  public static KMeansModel train(double[][] points, int nClusters, long seed) {
    var trainer =
        new KMeansTrainer(
            nClusters,
            MAX_ITERATIONS,
            new L2Distance(),
            KMeansTrainer.Initialisation.PLUSPLUS,
            THREADS,
            seed);
    return trainer.train(toDataset(points));
  }

  /** 学習したモデルの中心を、1 行に 1 つずつ並べた配列にする。 */
  public static double[][] centers(KMeansModel model) {
    return Arrays.stream(model.getCentroidVectors())
        .map(DenseVector::toArray)
        .toArray(double[][]::new);
  }

  /** Tribuo で学習した中心に自作の割り当てをして、自作と同じ定義の SSE を求める。 */
  public static double sse(double[][] points, int nClusters, long seed) {
    double[][] centers = centers(train(points, nClusters, seed));
    return KMeans.sumOfSquaredErrors(points, KMeans.assignClusters(points, centers), centers);
  }

  /** シードを 1 ずつずらして nInit 回学習し、最小の SSE を返す。 */
  public static double bestSse(double[][] points, int nClusters, long seed, int nInit) {
    return LongStream.range(0, nInit)
        .mapToDouble(i -> sse(points, nClusters, seed + i))
        .min()
        .orElseThrow();
  }
}
