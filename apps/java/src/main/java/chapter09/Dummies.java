package chapter09;

import chapter02.Row;
import chapter02.Table;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** カテゴリ値の列を、カテゴリごとの 0 と 1 の列（ダミー変数）に変える。 */
public final class Dummies {
  private Dummies() {}

  /** 欠損値（空欄）を除いたカテゴリを辞書順に並べ、先頭を除いて返す（pandas の drop_first=True と同じ）。 */
  public static List<String> categories(List<String> values) {
    return values.stream().filter(v -> !v.isBlank()).distinct().sorted().skip(1).toList();
  }

  /** 列を取り除き、カテゴリごとに「列名_カテゴリ」の列を末尾に加える。値が一致すれば "1"、それ以外は "0"。 */
  public static Table encode(Table table, String column, List<String> categories) {
    List<String> dummyColumns = categories.stream().map(c -> column + "_" + c).toList();
    List<String> columns =
        Stream.concat(
                table.columns().stream().filter(c -> !c.equals(column)), dummyColumns.stream())
            .toList();
    List<Row> rows = table.rows().stream().map(row -> encodeRow(row, column, categories)).toList();
    return new Table(columns, rows);
  }

  private static Row encodeRow(Row row, String column, List<String> categories) {
    Map<String, String> cells = new HashMap<>(row.cells());
    String value = cells.remove(column);
    for (String category : categories) {
      cells.put(column + "_" + category, category.equals(value) ? "1" : "0");
    }
    return new Row(cells);
  }
}
