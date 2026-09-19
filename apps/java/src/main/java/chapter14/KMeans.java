package chapter14;

import chapter07.Matrix;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

/** K-means によるクラスタリング。点は double の配列、点の集まりは 2 次元配列で表す。 */
public final class KMeans {
  /** 更新の回数の既定の上限（scikit-learn の KMeans と同じ） */
  public static final int DEFAULT_MAX_ITERATIONS = 300;

  /** 初期中心を試す回数の既定値（scikit-learn の KMeans の n_init と同じ） */
  public static final int DEFAULT_N_INIT = 10;

  private KMeans() {}

  /** 2 点間の距離の 2 乗。 */
  public static double squaredDistance(double[] a, double[] b) {
    double sum = 0;
    for (int j = 0; j < a.length; j++) {
      double d = a[j] - b[j];
      sum += d * d;
    }
    return sum;
  }

  /** 各点を、最も近い中心のクラスタ番号に割り当てる。 */
  public static int[] assignClusters(double[][] points, double[][] centers) {
    int[] labels = new int[points.length];
    for (int i = 0; i < points.length; i++) {
      int nearest = 0;
      for (int k = 1; k < centers.length; k++) {
        if (squaredDistance(points[i], centers[k]) < squaredDistance(points[i], centers[nearest])) {
          nearest = k;
        }
      }
      labels[i] = nearest;
    }
    return labels;
  }

  /** クラスタごとに、割り当てられた点の平均を新しい中心にする。 */
  public static double[][] updateCenters(double[][] points, int[] labels, double[][] previous) {
    double[][] sums = new double[previous.length][previous[0].length];
    int[] counts = new int[previous.length];
    for (int i = 0; i < points.length; i++) {
      counts[labels[i]]++;
      for (int j = 0; j < points[i].length; j++) {
        sums[labels[i]][j] += points[i][j];
      }
    }
    for (int k = 0; k < sums.length; k++) {
      if (counts[k] == 0) {
        sums[k] = previous[k].clone();
        continue;
      }
      for (int j = 0; j < sums[k].length; j++) {
        sums[k][j] /= counts[k];
      }
    }
    return sums;
  }

  /** 各点と、所属するクラスタの中心との距離の 2 乗の合計（誤差平方和）。 */
  public static double sumOfSquaredErrors(double[][] points, int[] labels, double[][] centers) {
    double sum = 0;
    for (int i = 0; i < points.length; i++) {
      sum += squaredDistance(points[i], centers[labels[i]]);
    }
    return sum;
  }

  /** 中心が変わらなくなるまで、割り当てと中心の更新を繰り返す（最大 300 回）。 */
  public static KMeansResult fit(double[][] points, double[][] initialCenters) {
    return fit(points, initialCenters, DEFAULT_MAX_ITERATIONS);
  }

  /** 中心が変わらなくなるか、更新の回数が maxIterations に達するまで、割り当てと中心の更新を繰り返す。 */
  public static KMeansResult fit(double[][] points, double[][] initialCenters, int maxIterations) {
    double[][] centers = initialCenters;
    for (int iteration = 0; iteration < maxIterations; iteration++) {
      double[][] next = updateCenters(points, assignClusters(points, centers), centers);
      if (Arrays.deepEquals(next, centers)) {
        break;
      }
      centers = next;
    }
    int[] labels = assignClusters(points, centers);
    return new KMeansResult(
        Arrays.stream(labels).boxed().toList(),
        Matrix.of(centers),
        sumOfSquaredErrors(points, labels, centers));
  }

  /** シード付きの乱数で点を並べ替え、先頭から nClusters 個を初期中心にする（点は写して返す）。 */
  public static double[][] chooseInitialCenters(double[][] points, int nClusters, long seed) {
    List<Integer> indices = new ArrayList<>(IntStream.range(0, points.length).boxed().toList());
    Collections.shuffle(indices, new Random(seed));
    return indices.stream().limit(nClusters).map(i -> points[i].clone()).toArray(double[][]::new);
  }

  /** クラスタ数ごとに、初期中心を 10 通り試した最小の SSE。 */
  public static Map<Integer, Double> sseByClusterCount(
      double[][] points, List<Integer> clusterCounts, long seed) {
    return sseByClusterCount(points, clusterCounts, seed, DEFAULT_N_INIT);
  }

  /** クラスタ数ごとに、初期中心を nInit 通り試した最小の SSE。 */
  public static Map<Integer, Double> sseByClusterCount(
      double[][] points, List<Integer> clusterCounts, long seed, int nInit) {
    Map<Integer, Double> sse = new LinkedHashMap<>();
    for (int n : clusterCounts) {
      sse.put(n, fitWithRestarts(points, n, seed, nInit).sse());
    }
    return sse;
  }

  /** シードを 1 ずつずらして初期中心を nInit 通り選び、SSE が最小の結果を返す。 */
  public static KMeansResult fitWithRestarts(
      double[][] points, int nClusters, long seed, int nInit) {
    List<double[][]> candidates =
        IntStream.range(0, nInit)
            .mapToObj(i -> chooseInitialCenters(points, nClusters, seed + i))
            .toList();
    return best(points, candidates);
  }

  /** 初期中心の候補ごとにクラスタリングし、SSE が最小の結果を返す。 */
  public static KMeansResult best(double[][] points, List<double[][]> initialCenterCandidates) {
    return initialCenterCandidates.stream()
        .map(centers -> fit(points, centers))
        .min(Comparator.comparingDouble(KMeansResult::sse))
        .orElseThrow();
  }
}
