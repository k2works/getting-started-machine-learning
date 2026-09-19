package chapter07;

import static org.assertj.core.api.Assertions.assertThat;

import chapter02.Row;
import chapter02.Table;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CinemaTest {
  private static final String HEADER = "cinema_id,SNS1,SNS2,actor,original,sales\n";

  private static Table table(String... sns2AndSales) {
    List<Row> rows =
        java.util.Arrays.stream(sns2AndSales)
            .map(pair -> pair.split(","))
            .map(cells -> new Row(Map.of("SNS2", cells[0], "sales", cells[1])))
            .toList();
    return new Table(List.of("SNS2", "sales"), rows);
  }

  private static List<String> sns2(Table table) {
    return table.rows().stream().map(row -> row.text("SNS2")).toList();
  }

  @Nested
  @DisplayName("外れ値の除去")
  class RemoveOutliers {
    @Test
    @DisplayName("SNS2 が 1000 を超え売上が 8500 未満の行を取り除く")
    void remove() {
      var table = table("1200,8000", "600,9500");

      assertThat(sns2(Cinema.removeOutliers(table))).containsExactly("600");
    }

    @Test
    @DisplayName("条件の片方だけを満たす行は残す")
    void keep() {
      var table = table("1200,9800", "600,8000");

      assertThat(sns2(Cinema.removeOutliers(table))).containsExactly("1200", "600");
    }
  }

  @Test
  @DisplayName("外れ値を除き特徴量を選んで分割し欠損値を補完する")
  void prepare(@TempDir Path directory) throws IOException {
    Path csvFile = directory.resolve("cinema.csv");
    Files.writeString(
        csvFile,
        HEADER
            + "1,100,300,9000.0,0,9200\n"
            + "2,,400,9500.0,1,9800\n"
            + "3,300,500,,1,10100\n"
            + "4,150,1200,8800.0,0,8100\n"
            + "5,250,700,9900.0,1,10300\n"
            + "6,120,650,9100.0,0,9400\n");

    var split = Cinema.prepare(csvFile, 0.4, 0);

    assertThat(split.xTrain().getFirst().columns())
        .containsExactly("SNS1", "SNS2", "actor", "original");
    assertThat(List.of(split.xTrain().size(), split.xTest().size())).containsExactly(3, 2);
    assertThat(split.tTrain()).doesNotContain(8100.0);
    assertThat(split.tTest()).doesNotContain(8100.0);
  }
}
