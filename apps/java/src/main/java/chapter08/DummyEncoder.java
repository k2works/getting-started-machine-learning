package chapter08;

import chapter02.Row;
import chapter02.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** カテゴリ値の列を、最初のカテゴリを除いたカテゴリごとの 0 と 1 の列（ダミー変数）にする前処理。 */
public record DummyEncoder(List<String> columns) implements Transformer {
  public DummyEncoder {
    columns = List.copyOf(columns);
  }

  @Override
  public FittedTransformer fit(Table x) {
    return new FittedDummyEncoder(dummiesOf(x));
  }

  /** 列ごとに、ダミー変数にするカテゴリ（最初のカテゴリを除く）を求める。 */
  private Map<String, List<String>> dummiesOf(Table x) {
    Map<String, List<String>> dummies = new LinkedHashMap<>();
    for (String column : columns) {
      List<String> categories = categoriesOf(x, column);
      dummies.put(column, categories.subList(1, categories.size()));
    }
    return dummies;
  }

  /** 列の値を重複なく並べ替える。欠損値は除く。 */
  private static List<String> categoriesOf(Table x, String column) {
    return x.rows().stream()
        .filter(row -> !row.isMissing(column))
        .map(row -> row.text(column))
        .distinct()
        .sorted()
        .toList();
  }

  /** fit で求めたカテゴリを持ち、どのデータにも同じダミー変数の列を作る。 */
  public record FittedDummyEncoder(Map<String, List<String>> dummies) implements FittedTransformer {
    public FittedDummyEncoder {
      // Map.copyOf は順序を保たないので、列の順を保つ LinkedHashMap に写す。
      // subList が返す部分リストはシリアライズできないので、List.copyOf で写す
      Map<String, List<String>> copy = new LinkedHashMap<>();
      dummies.forEach((column, categories) -> copy.put(column, List.copyOf(categories)));
      dummies = Collections.unmodifiableMap(copy);
    }

    @Override
    public Table transform(Table x) {
      return encode(x, dummies);
    }
  }

  /** 元の列を除き、ダミー変数の列を末尾に足す。 */
  static Table encode(Table x, Map<String, List<String>> dummies) {
    List<String> columns = new ArrayList<>(x.columns());
    dummies.forEach(
        (column, categories) -> {
          columns.remove(column);
          categories.forEach(category -> columns.add(column + "_" + category));
        });
    List<Row> rows = x.rows().stream().map(row -> encode(row, dummies)).toList();
    return new Table(columns, rows);
  }

  private static Row encode(Row row, Map<String, List<String>> dummies) {
    Row encoded = row;
    for (Map.Entry<String, List<String>> entry : dummies.entrySet()) {
      String value = row.text(entry.getKey());
      for (String category : entry.getValue()) {
        encoded =
            Rows.with(encoded, entry.getKey() + "_" + category, value.equals(category) ? "1" : "0");
      }
    }
    return encoded;
  }
}
