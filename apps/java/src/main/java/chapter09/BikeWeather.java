package chapter09;

import chapter02.Row;
import chapter02.Table;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** 自転車の利用者数（bike.tsv）と天気（weather.csv）の結合と集計。 */
public final class BikeWeather {
  /** weather.csv の文字コード */
  public static final Charset SHIFT_JIS = Charset.forName("Shift_JIS");

  private static final String KEY = "weather_id";

  private BikeWeather() {}

  /** タブ区切りの bike.tsv（UTF-8）を読み込む。 */
  public static Table loadBike(Path tsvFile) throws IOException {
    return DelimitedFiles.load(tsvFile, StandardCharsets.UTF_8, "\t");
  }

  /** Shift_JIS の weather.csv を読み込む。 */
  public static Table loadWeather(Path csvFile) throws IOException {
    return DelimitedFiles.load(csvFile, SHIFT_JIS, ",");
  }

  /** 天気 ID をキーにした Map を引いて、天気の列を加える（内部結合）。天気の表に無い ID の行は残さない。 */
  public static Table joinWeather(Table bike, Table weather) {
    Map<String, Row> byId =
        weather.rows().stream()
            .collect(Collectors.toMap(row -> row.text(KEY), Function.identity()));
    List<String> added = weather.columns().stream().filter(c -> !KEY.equals(c)).toList();
    List<Row> rows =
        bike.rows().stream()
            .filter(row -> byId.containsKey(row.text(KEY)))
            .map(row -> join(row, byId.get(row.text(KEY)), added))
            .toList();
    return new Table(Stream.concat(bike.columns().stream(), added.stream()).toList(), rows);
  }

  private static Row join(Row row, Row found, List<String> added) {
    Map<String, String> cells = new HashMap<>(row.cells());
    added.forEach(column -> cells.put(column, found.text(column)));
    return new Row(cells);
  }

  /** 天気ごとの平均利用者数を、多い順に並べて返す。 */
  public static Map<String, Double> meanCountByWeather(Table joined) {
    Map<String, Double> means =
        joined.rows().stream()
            .collect(
                Collectors.groupingBy(
                    row -> row.text("weather"),
                    Collectors.averagingDouble(row -> row.number("cnt").orElseThrow())));
    return means.entrySet().stream()
        .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
        .collect(
            Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
  }
}
