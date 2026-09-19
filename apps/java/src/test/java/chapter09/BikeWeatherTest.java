package chapter09;

import static org.assertj.core.api.Assertions.assertThat;

import chapter02.Row;
import chapter02.Table;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BikeWeatherTest {
  @TempDir Path directory;

  private static Table table(List<String> columns, List<Map<String, String>> rows) {
    return new Table(columns, rows.stream().map(Row::new).toList());
  }

  @Test
  @DisplayName("タブ区切りのファイルを読み込む")
  void loadsTsv() throws IOException {
    Path tsvFile = directory.resolve("bike.tsv");
    Files.writeString(tsvFile, "dteday\tweather_id\tcnt\n2030-04-01\t1\t120\n");

    Table bike = BikeWeather.loadBike(tsvFile);

    assertThat(bike.columns()).containsExactly("dteday", "weather_id", "cnt");
    assertThat(bike.rows().getFirst().text("weather_id")).isEqualTo("1");
    assertThat(bike.rows().getFirst().text("cnt")).isEqualTo("120");
  }

  @Test
  @DisplayName("Shift_JIS のファイルを読み込む")
  void loadsShiftJis() throws IOException {
    Path csvFile = directory.resolve("weather.csv");
    Files.writeString(csvFile, "weather_id,weather\n1,晴れ\n", Charset.forName("Shift_JIS"));

    Table weather = BikeWeather.loadWeather(csvFile);

    assertThat(weather.rows().getFirst().text("weather")).isEqualTo("晴れ");
  }

  @Test
  @DisplayName("Shift_JIS のファイルを UTF-8 として読むと例外になる")
  void shiftJisIsNotUtf8() throws IOException {
    Path csvFile = directory.resolve("weather.csv");
    Files.writeString(csvFile, "weather_id,weather\n1,晴れ\n", Charset.forName("Shift_JIS"));

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> DelimitedFiles.load(csvFile, StandardCharsets.UTF_8, ","))
        .isInstanceOf(java.nio.charset.MalformedInputException.class);
  }

  @Test
  @DisplayName("天気 ID で天気の名前を結合する")
  void joinsWeather() {
    Table bike =
        table(
            List.of("weather_id", "cnt"),
            List.of(
                Map.of("weather_id", "2", "cnt", "80"), Map.of("weather_id", "1", "cnt", "120")));
    Table weather =
        table(
            List.of("weather_id", "weather"),
            List.of(
                Map.of("weather_id", "1", "weather", "晴れ"),
                Map.of("weather_id", "2", "weather", "曇り")));

    Table joined = BikeWeather.joinWeather(bike, weather);

    assertThat(joined.columns()).containsExactly("weather_id", "cnt", "weather");
    assertThat(joined.rows()).extracting(r -> r.text("cnt")).containsExactly("80", "120");
    assertThat(joined.rows()).extracting(r -> r.text("weather")).containsExactly("曇り", "晴れ");
  }

  @Test
  @DisplayName("天気の表に無い天気 ID の行は残さない")
  void dropsUnknownWeather() {
    Table bike =
        table(
            List.of("weather_id", "cnt"),
            List.of(
                Map.of("weather_id", "1", "cnt", "120"), Map.of("weather_id", "9", "cnt", "30")));
    Table weather =
        table(
            List.of("weather_id", "weather"), List.of(Map.of("weather_id", "1", "weather", "晴れ")));

    Table joined = BikeWeather.joinWeather(bike, weather);

    assertThat(joined.rows()).extracting(r -> r.text("cnt")).containsExactly("120");
  }

  @Test
  @DisplayName("天気ごとの平均利用者数を多い順に求める")
  void meanCountByWeather() {
    Table joined =
        table(
            List.of("weather", "cnt"),
            List.of(
                Map.of("weather", "雨", "cnt", "20"),
                Map.of("weather", "晴れ", "cnt", "100"),
                Map.of("weather", "晴れ", "cnt", "140")));

    assertThat(BikeWeather.meanCountByWeather(joined))
        .containsExactly(Map.entry("晴れ", 120.0), Map.entry("雨", 20.0));
  }
}
