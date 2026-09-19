package chapter08;

import chapter02.Row;
import chapter02.Table;
import java.util.LinkedHashMap;
import java.util.Map;

/** 文字列の列の欠損値を、訓練データで最も多い値（最頻値）で補完する前処理。 */
public record MostFrequentImputer(String column) implements Transformer {
  @Override
  public FittedTransformer fit(Table x) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    x.rows().stream()
        .filter(row -> !row.isMissing(column))
        .forEach(row -> counts.merge(row.text(column), 1, Integer::sum));
    String mostFrequent = null;
    int bestCount = 0;
    for (Map.Entry<String, Integer> entry : counts.entrySet()) {
      if (entry.getValue() > bestCount) {
        mostFrequent = entry.getKey();
        bestCount = entry.getValue();
      }
    }
    return new FittedMostFrequentImputer(column, mostFrequent);
  }

  /** fit で求めた最頻値を持ち、欠損値を補完する。 */
  public record FittedMostFrequentImputer(String column, String mostFrequent)
      implements FittedTransformer {
    @Override
    public Table transform(Table x) {
      return new Table(x.columns(), x.rows().stream().map(this::fill).toList());
    }

    private Row fill(Row row) {
      return row.isMissing(column) ? Rows.with(row, column, mostFrequent) : row;
    }
  }
}
