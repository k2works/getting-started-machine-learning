package chapter07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RegressionMetricsTest {
  private static final List<Double> T = List.of(3.0, 5.0, 7.0);

  @Nested
  @DisplayName("MAE")
  class MeanAbsoluteError {
    @Test
    @DisplayName("誤差の絶対値の平均を求める")
    void average() {
      assertThat(RegressionMetrics.meanAbsoluteError(T, List.of(2.0, 5.0, 9.0)))
          .isCloseTo(1.0, within(1e-12));
    }

    @Test
    @DisplayName("予測が大きく外れるほど値が大きくなる")
    void larger() {
      assertThat(RegressionMetrics.meanAbsoluteError(T, List.of(1.0, 8.0, 7.0)))
          .isCloseTo(5.0 / 3, within(1e-12));
    }

    @Test
    @DisplayName("実測値と予測値の件数が違えばエラーになる")
    void sizeMismatch() {
      assertThatThrownBy(() -> RegressionMetrics.meanAbsoluteError(T, List.of(1.0)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("実測値と予測値の件数が違います");
    }
  }

  @Test
  @DisplayName("RMSE は誤差の 2 乗の平均の平方根になる")
  void rootMeanSquaredError() {
    assertThat(RegressionMetrics.rootMeanSquaredError(T, List.of(2.0, 5.0, 9.0)))
        .isCloseTo(Math.sqrt(5.0 / 3), within(1e-12));
  }

  @Nested
  @DisplayName("R²")
  class R2Score {
    @Test
    @DisplayName("予測がすべて正解なら 1 になる")
    void perfect() {
      assertThat(RegressionMetrics.r2Score(T, T)).isCloseTo(1.0, within(1e-12));
    }

    @Test
    @DisplayName("平均値を予測し続けるモデルより良い分だけ 1 に近づく")
    void betterThanMean() {
      assertThat(RegressionMetrics.r2Score(T, List.of(2.0, 5.0, 9.0)))
          .isCloseTo(1 - 5.0 / 8, within(1e-12));
    }
  }
}
