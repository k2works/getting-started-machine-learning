package chapter14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import chapter02.Features;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpendingTest {
  private static final String HEADER =
      "Channel,Region,Fresh,Milk,Grocery,Frozen,Detergents_Paper,Delicassen\n";

  @TempDir Path directory;

  @Test
  @DisplayName("Channel と Region を除いた支出額の列を読み込む")
  void loadsSpendingColumns() throws IOException {
    Path csv = directory.resolve("wholesale.csv");
    Files.writeString(csv, HEADER + "1,2,100,200,300,400,500,600\n");

    List<Features> x = Spending.load(csv);

    assertThat(x.getFirst().columns())
        .containsExactly("Fresh", "Milk", "Grocery", "Frozen", "Detergents_Paper", "Delicassen");
    assertThat(x.getFirst().values()).containsExactly(100, 200, 300, 400, 500, 600);
  }

  @Test
  @DisplayName("列ごとに平均 0・標準偏差 1 の点の配列に変換する")
  void standardizesToPoints() {
    List<String> columns = List.of("Fresh", "Milk");
    List<Features> x =
        List.of(
            new Features(columns, new double[] {10, 5}),
            new Features(columns, new double[] {20, 5}),
            new Features(columns, new double[] {30, 8}));

    double[][] points = Spending.standardize(x);

    for (int j = 0; j < 2; j++) {
      int column = j;
      double[] values = Arrays.stream(points).mapToDouble(p -> p[column]).toArray();
      double mean = Arrays.stream(values).average().orElseThrow();
      double variance =
          Arrays.stream(values).map(v -> (v - mean) * (v - mean)).average().orElseThrow();
      assertThat(mean).isCloseTo(0.0, within(1e-12));
      assertThat(Math.sqrt(variance)).isCloseTo(1.0, within(1e-12));
    }
  }

  @Test
  @DisplayName("クラスタごとの件数と平均を件数の多い順に並べる")
  void summarizesClusters() {
    List<String> columns = List.of("Fresh", "Milk");
    List<Features> x =
        List.of(
            new Features(columns, new double[] {100, 20}),
            new Features(columns, new double[] {300, 40}),
            new Features(columns, new double[] {1000, 900}));

    List<ClusterSummary> summary = Spending.summarizeClusters(x, List.of(1, 1, 0));

    assertThat(summary)
        .containsExactly(
            new ClusterSummary(1, 2, Map.of("Fresh", 200.0, "Milk", 30.0)),
            new ClusterSummary(0, 1, Map.of("Fresh", 1000.0, "Milk", 900.0)));
  }
}
