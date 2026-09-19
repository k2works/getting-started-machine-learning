package chapter08;

import chapter02.Row;
import chapter02.Table;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** テスト用の表を作り、列の値を読む。 */
final class Tables {
  private Tables() {}

  /** 1 つ目の引数を列名の並び、残りを行として、カンマ区切りの文字列から表を作る。空欄は欠損値。 */
  static Table table(String header, String... lines) {
    List<String> columns = List.of(header.split(","));
    List<Row> rows = Arrays.stream(lines).map(line -> row(columns, line)).toList();
    return new Table(columns, rows);
  }

  private static Row row(List<String> columns, String line) {
    String[] values = line.split(",", -1);
    Map<String, String> cells = new HashMap<>();
    for (int i = 0; i < columns.size(); i++) {
      cells.put(columns.get(i), values[i]);
    }
    return new Row(cells);
  }

  /** 数値の列の値を、上から順に並べる。欠損値があれば失敗する。 */
  static List<Double> numbers(Table table, String name) {
    return table.rows().stream().map(row -> row.number(name).orElseThrow()).toList();
  }

  /** 文字列の列の値を、上から順に並べる。 */
  static List<String> texts(Table table, String name) {
    return table.rows().stream().map(row -> row.text(name)).toList();
  }
}
