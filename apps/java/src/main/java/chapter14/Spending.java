package chapter14;

import chapter02.Features;
import chapter02.Table;
import chapter09.Standardizer;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** 卸売業者の顧客ごとの支出額（Wholesale.csv）。 */
public final class Spending {
  /** 区分を表す番号で、支出額ではない列 */
  private static final Set<String> CATEGORIES = Set.of("Channel", "Region");

  private Spending() {}

  /** Channel と Region を除いた支出額の列を読み込む。欠損値があれば例外を投げる。 */
  public static List<Features> load(Path csvFile) throws IOException {
    Table table = Table.load(csvFile);
    List<String> columns = table.columns().stream().filter(c -> !CATEGORIES.contains(c)).toList();
    return table.rows().stream()
        .map(
            row ->
                new Features(
                    columns,
                    columns.stream().mapToDouble(c -> row.number(c).orElseThrow()).toArray()))
        .toList();
  }

  /** 第 9 章の標準化（件数で割る標準偏差）で列ごとにそろえ、1 件を 1 つの点とする配列にする。 */
  public static double[][] standardize(List<Features> x) {
    return Standardizer.fit(x).transform(x).stream().map(Features::values).toArray(double[][]::new);
  }

  /** クラスタごとの件数と、元の単位での列ごとの平均を、件数の多い順に並べる。 */
  public static List<ClusterSummary> summarizeClusters(List<Features> x, List<Integer> labels) {
    Map<Integer, List<Features>> members = new TreeMap<>();
    for (int i = 0; i < x.size(); i++) {
      members.computeIfAbsent(labels.get(i), k -> new ArrayList<>()).add(x.get(i));
    }
    return members.entrySet().stream()
        .map(e -> new ClusterSummary(e.getKey(), e.getValue().size(), means(e.getValue())))
        .sorted(Comparator.comparingInt(ClusterSummary::count).reversed())
        .toList();
  }

  private static Map<String, Double> means(List<Features> rows) {
    return rows.getFirst().columns().stream()
        .collect(
            Collectors.toMap(
                column -> column,
                column -> rows.stream().mapToDouble(f -> f.value(column)).average().orElseThrow(),
                (a, b) -> a,
                LinkedHashMap::new));
  }
}
