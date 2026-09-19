package chapter02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TableTest {
  private static final String HEADER = "\uFEFFがく片長さ,がく片幅,花弁長さ,花弁幅,種類\n";

  @TempDir Path directory;

  private Path writeCsv(String rows) throws IOException {
    return Files.writeString(directory.resolve("iris.csv"), HEADER + rows, StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("BOM 付き CSV を読み込むと列名に BOM が残らない")
  void stripsBom() throws IOException {
    Table table = Table.load(writeCsv("0.1,0.2,0.3,0.4,Iris-setosa\n"));

    assertThat(table.columns()).containsExactly("がく片長さ", "がく片幅", "花弁長さ", "花弁幅", "種類");
  }

  @Test
  @DisplayName("空欄は欠損値として読み込む")
  void blankIsMissing() throws IOException {
    Table table = Table.load(writeCsv("0.1,,0.3,0.4,Iris-setosa\n"));

    Row row = table.rows().getFirst();
    assertThat(row.number("がく片幅")).isEmpty();
    assertThat(row.number("がく片長さ")).hasValue(0.1);
    assertThat(row.text("種類")).isEqualTo("Iris-setosa");
  }

  @Test
  @DisplayName("行の最後の列が空欄でも欠損値として読み込む")
  void trailingBlankIsMissing() throws IOException {
    Table table = Table.load(writeCsv("0.1,0.2,0.3,,\n"));

    assertThat(table.rows().getFirst().number("花弁幅")).isEmpty();
    assertThat(table.rows().getFirst().text("種類")).isEmpty();
  }

  @Test
  @DisplayName("無い列を読み出すと列名を示すエラーになる")
  void unknownColumn() {
    Row row = new Row(Map.of("がく片長さ", "0.1"));

    assertThatThrownBy(() -> row.number("花弁幅"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("花弁幅");
  }

  @Test
  @DisplayName("Row は受け取った Map を写して持ち、元の Map の変更の影響を受けない")
  void rowCopiesCells() {
    var cells = new java.util.HashMap<String, String>();
    cells.put("がく片長さ", "0.1");
    Row row = new Row(cells);

    cells.put("がく片長さ", "0.9");

    assertThat(row.number("がく片長さ")).isEqualTo(OptionalDouble.of(0.1));
  }

  @Test
  @DisplayName("列ごとの欠損値の数を列の順に数える")
  void countsMissing() {
    Table table =
        new Table(
            List.of("がく片長さ", "がく片幅", "種類"),
            List.of(
                new Row(Map.of("がく片長さ", "0.1", "がく片幅", "0.2", "種類", "Iris-setosa")),
                new Row(Map.of("がく片長さ", "", "がく片幅", "0.3", "種類", "Iris-setosa")),
                new Row(Map.of("がく片長さ", "", "がく片幅", "", "種類", "Iris-virginica"))));

    assertThat(table.countMissing())
        .containsExactly(Map.entry("がく片長さ", 2), Map.entry("がく片幅", 1), Map.entry("種類", 0));
  }
}
