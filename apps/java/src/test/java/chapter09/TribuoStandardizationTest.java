package chapter09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TribuoStandardizationTest {
  private static final List<Double> TRAIN = List.of(5.5, 6.0, 7.5, 6.5);
  private static final List<Double> TEST = List.of(6.2, 8.0);

  @Test
  @DisplayName("Tribuo の MeanStdDevTransformation は件数から 1 を引いて割る標準偏差を使う")
  void tribuoUsesSampleStd() {
    double mean = TRAIN.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    double sumOfSquares = TRAIN.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
    double sampleStd = Math.sqrt(sumOfSquares / (TRAIN.size() - 1));

    List<Double> standardized = TribuoStandardization.standardize(TRAIN, TEST);

    assertThat(standardized.get(0)).isCloseTo((6.2 - mean) / sampleStd, within(1e-12));
    assertThat(standardized.get(1)).isCloseTo((8.0 - mean) / sampleStd, within(1e-12));
  }

  @Test
  @DisplayName("自作の標準化に件数から決まる係数を掛けると Tribuo の値になる")
  void ownTimesRatioEqualsTribuo() {
    var standardizer = Standardizer.fit(TRAIN.stream().map(Samples::rm).toList());
    double ratio = Math.sqrt((TRAIN.size() - 1.0) / TRAIN.size());

    List<Double> tribuo = TribuoStandardization.standardize(TRAIN, TEST);

    for (int i = 0; i < TEST.size(); i++) {
      double own = standardizer.transform(Samples.rm(TEST.get(i))).value("RM");
      assertThat(own * ratio).isCloseTo(tribuo.get(i), within(1e-12));
    }
  }
}
