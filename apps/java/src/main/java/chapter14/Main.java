package chapter14;

import chapter02.Features;
import dataset.DataDir;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/** 卸売業者の顧客を支出額で K-means にかけ、エルボー法の SSE とクラスタごとの特徴を表示する。 */
public final class Main {
  private static final long SEED = 0;
  private static final int N_INIT = KMeans.DEFAULT_N_INIT;
  private static final List<Integer> CLUSTER_COUNTS = IntStream.rangeClosed(1, 10).boxed().toList();
  private static final int N_CLUSTERS = 5;

  // Tribuo が学習の経過を標準エラーに出すので、警告以上だけにする。
  // ロガーはガベージコレクションで設定ごと消えないように、フィールドで持ち続ける
  private static final Logger TRIBUO_LOGGER = Logger.getLogger("org.tribuo");

  private Main() {}

  public static void main(String[] args) throws IOException {
    TRIBUO_LOGGER.setLevel(Level.WARNING);
    List<Features> x = Spending.load(DataDir.dataDir().resolve("Wholesale.csv"));
    List<String> columns = x.getFirst().columns();
    double[][] points = Spending.standardize(x);
    System.out.println("データ件数: " + x.size() + "（支出額 " + columns.size() + " 列）");
    System.out.println("クラスタ数ごとの SSE（初期中心 " + N_INIT + " 通りの最小値）:");
    System.out.println("クラスタ数\t自作\tTribuo（k-means++）");
    for (Map.Entry<Integer, Double> entry :
        KMeans.sseByClusterCount(points, CLUSTER_COUNTS, SEED, N_INIT).entrySet()) {
      int n = entry.getKey();
      double tribuo = TribuoKMeans.bestSse(points, n, SEED, N_INIT);
      System.out.println(
          n + "\t" + format("%.2f", entry.getValue()) + "\t" + format("%.2f", tribuo));
    }

    KMeansResult result = KMeans.fitWithRestarts(points, N_CLUSTERS, SEED, N_INIT);
    System.out.println();
    System.out.println("クラスタ数 " + N_CLUSTERS + " のクラスタごとの件数と平均支出額:");
    List<String> header = new ArrayList<>(List.of("クラスタ", "件数"));
    header.addAll(columns);
    System.out.println(String.join("\t", header));
    for (ClusterSummary summary : Spending.summarizeClusters(x, result.labels())) {
      List<String> cells =
          new ArrayList<>(
              List.of(String.valueOf(summary.cluster()), String.valueOf(summary.count())));
      columns.forEach(c -> cells.add(format("%.0f", summary.means().get(c))));
      System.out.println(String.join("\t", cells));
    }
  }

  private static String format(String pattern, double value) {
    return String.format(Locale.ROOT, pattern, value);
  }
}
