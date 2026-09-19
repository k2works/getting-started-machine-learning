package chapter02;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 列名の並びと行のリスト。データフレームのライブラリの代わりに使う、変更できない表。 */
public record Table(List<String> columns, List<Row> rows) {
  private static final String BOM = "\uFEFF";

  public Table {
    columns = List.copyOf(columns);
    rows = List.copyOf(rows);
  }

  /** BOM 付きの UTF-8 の CSV を読み込む。 */
  public static Table load(Path csvFile) throws IOException {
    List<String> lines = Files.readAllLines(csvFile, StandardCharsets.UTF_8);
    List<String> columns = List.of(stripBom(lines.getFirst()).split(","));
    List<Row> rows =
        lines.stream()
            .skip(1)
            .filter(line -> !line.isBlank())
            .map(line -> toRow(columns, line))
            .toList();
    return new Table(columns, rows);
  }

  private static Row toRow(List<String> columns, String line) {
    // 上限に -1 を渡すと、行末の空欄も空文字列として残る
    String[] values = line.split(",", -1);
    Map<String, String> cells = new HashMap<>();
    for (int i = 0; i < columns.size(); i++) {
      cells.put(columns.get(i), values[i]);
    }
    return new Row(cells);
  }

  private static String stripBom(String line) {
    return line.startsWith(BOM) ? line.substring(BOM.length()) : line;
  }

  /** 列ごとの欠損値の数を、列の順に並べて返す。 */
  public Map<String, Integer> countMissing() {
    Map<String, Integer> counts = new LinkedHashMap<>();
    for (String column : columns) {
      counts.put(column, (int) rows.stream().filter(row -> row.isMissing(column)).count());
    }
    return counts;
  }
}
