package chapter13;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import chapter02.Features;
import chapter02.Row;
import chapter02.Table;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BostonPcaTest {
  private static Row row(String crime, String rm, String price) {
    return new Row(Map.of("CRIME", crime, "RM", rm, "PRICE", price));
  }

  /** 架空の 4 件。RM の 3 件目が欠損値。 */
  private static Table bostonLike() {
    return new Table(
        List.of("CRIME", "RM", "PRICE"),
        List.of(
            row("high", "5", "10"),
            row("low", "6", "20"),
            row("very_low", "", "30"),
            row("low", "7", "40")));
  }

  @Test
  @DisplayName("CRIME をダミー変数の列に置き換える")
  void replacesCrimeWithDummies() {
    List<Features> x = BostonPca.standardize(bostonLike());

    assertThat(x.getFirst().columns())
        .containsExactly("RM", "PRICE", "CRIME_low", "CRIME_very_low");
  }

  @Test
  @DisplayName("欠損値を補完してから各列を平均 0 と標準偏差 1 にそろえる")
  void fillsAndStandardizes() {
    List<Features> x = BostonPca.standardize(bostonLike());

    for (String column : x.getFirst().columns()) {
      double[] values = x.stream().mapToDouble(f -> f.value(column)).toArray();
      double mean = Arrays.stream(values).average().orElseThrow();
      double variance =
          Arrays.stream(values).map(v -> (v - mean) * (v - mean)).average().orElseThrow();
      assertThat(mean).as(column).isCloseTo(0.0, within(1e-9));
      assertThat(Math.sqrt(variance)).as(column).isCloseTo(1.0, within(1e-9));
    }
  }
}
