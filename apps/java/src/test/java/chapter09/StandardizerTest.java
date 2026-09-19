package chapter09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import chapter02.Features;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StandardizerTest {
  private static final List<String> COLUMNS = List.of("RM", "LSTAT");

  private static Features row(double rm, double lstat) {
    return new Features(COLUMNS, new double[] {rm, lstat});
  }

  @Test
  @DisplayName("訓練データから列ごとの平均と標準偏差を求める")
  void fitsMeansAndStds() {
    var standardizer = Standardizer.fit(List.of(row(1, 10), row(2, 10), row(3, 40)));

    assertThat(standardizer.means().get("RM")).isCloseTo(2.0, within(1e-12));
    assertThat(standardizer.means().get("LSTAT")).isCloseTo(20.0, within(1e-12));
    assertThat(standardizer.stds().get("RM")).isCloseTo(Math.sqrt(2.0 / 3), within(1e-12));
    assertThat(standardizer.stds().get("LSTAT")).isCloseTo(Math.sqrt(200.0), within(1e-12));
  }

  @Test
  @DisplayName("訓練データの平均と標準偏差で別のデータを標準化する")
  void transformsOtherData() {
    var standardizer = new Standardizer(Map.of("RM", 2.0), Map.of("RM", 0.5));
    var other = List.of(1.0, 2.0, 4.0).stream().map(Samples::rm).toList();

    var standardized = standardizer.transform(other);

    assertThat(standardized).extracting(f -> f.value("RM")).containsExactly(-2.0, 0.0, 4.0);
  }

  @Test
  @DisplayName("すべて同じ値の列は 0 にする")
  void constantColumnBecomesZero() {
    var x = List.of(1.0, 1.0, 1.0).stream().map(Samples::rm).toList();

    var standardized = Standardizer.fit(x).transform(x);

    assertThat(standardized).extracting(f -> f.value("RM")).containsExactly(0.0, 0.0, 0.0);
  }

  @Test
  @DisplayName("標準化する列に無い列はそのまま残す")
  void keepsOtherColumns() {
    var standardizer = new Standardizer(Map.of("RM", 2.0), Map.of("RM", 0.5));

    var standardized = standardizer.transform(row(3, 7));

    assertThat(standardized).isEqualTo(row(2, 7));
  }
}
