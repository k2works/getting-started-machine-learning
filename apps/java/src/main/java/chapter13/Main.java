package chapter13;

import chapter02.Features;
import chapter07.Matrix;
import dataset.DataDir;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** ボストンの住宅価格の 15 列を主成分分析し、寄与率と主成分の意味を表示する。 */
public final class Main {
  private static final double THRESHOLD = 0.8;
  private static final int TOP_K = 3;
  private static final int COMPONENTS_TO_EXPLAIN = 2;

  private Main() {}

  public static void main(String[] args) throws IOException {
    List<Features> features = BostonPca.load(DataDir.dataDir().resolve("Boston.csv"));
    List<String> columns = features.getFirst().columns();
    Matrix x = BostonPca.toMatrix(features);
    PcaModel model = Pca.fit(x, columns.size());
    List<Double> ratios = model.explainedVarianceRatio();
    int needed = Pca.componentsNeeded(ratios, THRESHOLD);
    double cumulative = ratios.stream().limit(needed).mapToDouble(Double::doubleValue).sum();

    System.out.println("データ件数: " + x.rowCount() + ", 列数: " + columns.size());
    System.out.println(
        "寄与率: "
            + IntStream.range(0, needed)
                .mapToObj(i -> "PC" + (i + 1) + " " + format("%.4f", ratios.get(i)))
                .collect(Collectors.joining(", ")));
    System.out.println(
        "累積寄与率が "
            + THRESHOLD
            + " に届く主成分の数: "
            + needed
            + "（累積寄与率 "
            + format("%.4f", cumulative)
            + "）");
    double[][] components = model.components().toArray();
    for (int i = 0; i < COMPONENTS_TO_EXPLAIN; i++) {
      String loadings =
          Pca.topLoadings(components[i], columns, TOP_K).stream()
              .map(l -> l.column() + " " + format("%.3f", l.value()))
              .collect(Collectors.joining(", "));
      System.out.println("第 " + (i + 1) + " 主成分で影響の大きい列: " + loadings);
    }
  }

  private static String format(String pattern, double value) {
    return String.format(Locale.ROOT, pattern, value);
  }
}
