package chapter12;

import static chapter12.Samples.assertDoubles;
import static org.assertj.core.api.Assertions.assertThat;

import chapter02.Features;
import chapter02.Row;
import chapter02.Table;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BostonTest {
  @Nested
  @DisplayName("多項式特徴量")
  class PolynomialScalerTest {
    private final List<Features> x =
        List.of(
            new Features(List.of("a", "b"), new double[] {1, 10}),
            new Features(List.of("a", "b"), new double[] {3, 30}));

    @Test
    @DisplayName("平均と件数 n で割る標準偏差を求める")
    void fit() {
      var scaler = PolynomialScaler.fit(x);

      assertThat(scaler)
          .isEqualTo(
              new PolynomialScaler(List.of("a", "b"), List.of(2.0, 20.0), List.of(1.0, 10.0)));
    }

    @Test
    @DisplayName("元の列・2 乗の列・積の列の順に名前を付ける")
    void featureNames() {
      assertThat(PolynomialScaler.fit(x).featureNames())
          .containsExactly("a", "b", "a^2", "a b", "b^2");
    }

    @Test
    @DisplayName("標準化してから 2 乗と積を作る")
    void transform() {
      var scaler = PolynomialScaler.fit(x);

      var z = scaler.transform(List.of(new Features(List.of("a", "b"), new double[] {4, 10})));

      assertDoubles(
          List.of(2.0, -1.0, 4.0, -2.0, 1.0),
          Arrays.stream(z.toArray()[0]).boxed().toList(),
          1e-12);
    }
  }

  @Test
  @DisplayName("z スコアの絶対値が閾値を超える値を持つ行を除く")
  void removeOutliers() {
    // 10 行のうち 1 行だけ v が大きく外れている
    List<Row> rows =
        IntStream.range(0, 10)
            .mapToObj(i -> new Row(Map.of("id", String.valueOf(i), "v", i == 9 ? "100" : "1")))
            .toList();
    var table = new Table(List.of("id", "v"), rows);

    Table kept = Boston.removeOutliers(table, List.of("v"), 2.0);

    assertThat(kept.rows()).extracting(row -> row.text("id")).doesNotContain("9").hasSize(9);
  }
}
