package chapter08;

import chapter02.Row;
import chapter02.Table;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 数値の列の欠損値を、同じグループ（by の列の値の組）の中央値で補完する前処理。 */
public record GroupMedianImputer(String column, List<String> by) implements Transformer {
  public GroupMedianImputer {
    by = List.copyOf(by);
  }

  @Override
  public FittedTransformer fit(Table x) {
    Map<List<String>, List<Double>> groups =
        x.rows().stream()
            .filter(row -> !row.isMissing(column))
            .collect(
                Collectors.groupingBy(
                    row -> groupOf(row, by),
                    Collectors.mapping(
                        row -> row.number(column).orElseThrow(), Collectors.toList())));
    Map<List<String>, Double> medians =
        groups.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, entry -> median(entry.getValue())));
    double overallMedian = median(groups.values().stream().flatMap(List::stream).toList());
    return new FittedGroupMedianImputer(column, by, medians, overallMedian);
  }

  /** 行のグループ。by の列の値を並べたリストで、Map のキーに使う。 */
  static List<String> groupOf(Row row, List<String> by) {
    return by.stream().map(row::text).toList();
  }

  /** 中央値。件数が偶数なら中央の 2 つの平均。 */
  static double median(List<Double> values) {
    double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
    int middle = sorted.length / 2;
    return sorted.length % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
  }

  /** fit で求めたグループごとの中央値と全体の中央値を持ち、欠損値を補完する。 */
  public record FittedGroupMedianImputer(
      String column, List<String> by, Map<List<String>, Double> medians, double overallMedian)
      implements FittedTransformer {
    public FittedGroupMedianImputer {
      by = List.copyOf(by);
      medians = Map.copyOf(medians);
    }

    @Override
    public Table transform(Table x) {
      return new Table(x.columns(), x.rows().stream().map(this::fill).toList());
    }

    private Row fill(Row row) {
      if (!row.isMissing(column)) {
        return row;
      }
      double median = medians.getOrDefault(groupOf(row, by), overallMedian);
      return Rows.with(row, column, String.valueOf(median));
    }
  }
}
