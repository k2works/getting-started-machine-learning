package chapter11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import chapter07.RegressionMetrics;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MetricsTest {
  @Nested
  @DisplayName("混同行列")
  class ConfusionMatrixTest {
    @Test
    @DisplayName("正例と負例の予測の当たり外れを数える")
    void confusionMatrix() {
      var actual = List.of(1, 1, 1, 0, 0);
      var predicted = List.of(1, 1, 0, 1, 0);

      assertThat(ConfusionMatrix.of(actual, predicted, 1))
          .isEqualTo(new ConfusionMatrix(2, 1, 1, 1));
    }

    @Test
    @DisplayName("どちらのラベルを正例とするかで数え方が変わる")
    void positiveLabel() {
      var actual = List.of(1, 1, 1, 0, 0, 0);
      var predicted = List.of(1, 0, 0, 0, 0, 1);

      assertThat(ConfusionMatrix.of(actual, predicted, 0))
          .isEqualTo(new ConfusionMatrix(2, 2, 1, 1));
    }

    @Test
    @DisplayName("正解と予測の件数が違えばエラーになる")
    void sizeMismatch() {
      assertThatThrownBy(() -> ConfusionMatrix.of(List.of(1, 0), List.of(1, 0, 1), 1))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("正解と予測の件数が違います（正解 2 件、予測 3 件）");
    }
  }

  @Nested
  @DisplayName("適合率・再現率・F 値")
  class PrecisionRecallF1Test {
    private final ConfusionMatrix cm = new ConfusionMatrix(3, 1, 2, 4);

    @Test
    @DisplayName("適合率は正例と予測したうち本当に正例だった割合")
    void precision() {
      assertThat(Metrics.precision(cm)).isCloseTo(0.75, within(1e-12));
    }

    @Test
    @DisplayName("再現率は本当の正例のうち正例と予測できた割合")
    void recall() {
      assertThat(Metrics.recall(cm)).isCloseTo(0.6, within(1e-12));
    }

    @Test
    @DisplayName("F 値は適合率と再現率の調和平均")
    void f1Score() {
      assertThat(Metrics.f1Score(cm)).isCloseTo(2 * 0.75 * 0.6 / (0.75 + 0.6), within(1e-12));
    }

    @Test
    @DisplayName("正例を 1 件も当てられなければ適合率と再現率と F 値は 0")
    void zeroDenominator() {
      var missed = new ConfusionMatrix(0, 0, 3, 5);

      assertThat(
              List.of(Metrics.precision(missed), Metrics.recall(missed), Metrics.f1Score(missed)))
          .containsExactly(0.0, 0.0, 0.0);
    }
  }

  @Nested
  @DisplayName("回帰の評価指標")
  class RegressionTest {
    @Test
    @DisplayName("誤差の 2 乗の平均と平方根と絶対値の平均を求める")
    void errors() {
      var actual = List.of(3.0, 5.0, 8.0);
      var predicted = List.of(2.0, 5.0, 10.0);

      assertThat(Metrics.meanSquaredError(actual, predicted)).isCloseTo(5.0 / 3, within(1e-12));
      assertThat(RegressionMetrics.rootMeanSquaredError(actual, predicted))
          .isCloseTo(Math.sqrt(5.0 / 3), within(1e-12));
      assertThat(RegressionMetrics.meanAbsoluteError(actual, predicted))
          .isCloseTo(1.0, within(1e-12));
    }

    @Test
    @DisplayName("大きく外れた予測があると RMSE は MAE より大きく増える")
    void outlierSensitivity() {
      var actual = List.of(3.0, 5.0, 8.0, 10.0);
      var predicted = List.of(2.0, 5.0, 10.0, 30.0);

      assertThat(RegressionMetrics.rootMeanSquaredError(actual, predicted))
          .isCloseTo(Math.sqrt(101.25), within(1e-12));
      assertThat(RegressionMetrics.meanAbsoluteError(actual, predicted))
          .isCloseTo(5.75, within(1e-12));
    }

    @Test
    @DisplayName("MSE も正解と予測の件数が違えばエラーになる")
    void sizeMismatch() {
      assertThatThrownBy(() -> Metrics.meanSquaredError(List.of(1.0, 2.0), List.of(1.0)))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("評価関数")
  class MetricFunctionTest {
    @Test
    @DisplayName("正解率は正解と予測が一致した割合")
    void accuracy() {
      assertThat(Metrics.accuracy(List.of(1, 0, 1, 0), List.of(1, 1, 1, 0)))
          .isCloseTo(0.75, within(1e-12));
    }

    @Test
    @DisplayName("正解率も正解と予測の件数が違えばエラーになる")
    void accuracySizeMismatch() {
      assertThatThrownBy(() -> Metrics.accuracy(List.of(1, 0, 1), List.of(1, 0)))
          .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("混同行列から求める指標を正解と予測から求める評価関数に変える")
    void classificationMetric() {
      var actual = List.of(1, 1, 1, 0, 0);
      var predicted = List.of(1, 0, 0, 1, 0);

      Metric<Integer> precision = Metrics.classificationMetric(Metrics::precision, 1);
      Metric<Integer> recall = Metrics.classificationMetric(Metrics::recall, 1);

      assertThat(precision.applyAsDouble(actual, predicted)).isCloseTo(0.5, within(1e-12));
      assertThat(recall.applyAsDouble(actual, predicted)).isCloseTo(1.0 / 3, within(1e-12));
    }
  }
}
