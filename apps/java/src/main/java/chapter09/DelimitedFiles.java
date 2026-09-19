package chapter09;

import chapter02.Row;
import chapter02.Table;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 文字コードと区切り文字を指定して、区切り文字で区切ったファイルを {@link Table} に読み込む。 */
public final class DelimitedFiles {
  private DelimitedFiles() {}

  /** 1 行目を列名として読み込む。文字コードが違えば MalformedInputException を投げる。 */
  public static Table load(Path file, Charset charset, String delimiter) throws IOException {
    String separator = Pattern.quote(delimiter);
    List<String> lines = Files.readAllLines(file, charset);
    List<String> columns = List.of(lines.getFirst().split(separator));
    List<Row> rows =
        lines.stream()
            .skip(1)
            .filter(line -> !line.isBlank())
            .map(line -> toRow(columns, line.split(separator, -1)))
            .toList();
    return new Table(columns, rows);
  }

  private static Row toRow(List<String> columns, String[] values) {
    Map<String, String> cells = new HashMap<>();
    for (int i = 0; i < columns.size(); i++) {
      cells.put(columns.get(i), values[i]);
    }
    return new Row(cells);
  }
}
