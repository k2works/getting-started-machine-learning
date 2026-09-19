package chapter12;

import static chapter12.Samples.assertDoubles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import chapter02.Features;
import chapter07.LinearModel;
import chapter07.LinearRegression;
import chapter07.Matrix;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RidgeTest {
  @Nested
  @DisplayName("学習")
  class FitTest {
    @Test
    @DisplayName("alpha が 0 なら最小二乗法と同じ係数と切片になる")
    void leastSquares() {
      var x = Matrix.of(new double[][] {{1}, {2}, {3}});
      var t = List.of(3.0, 5.0, 7.0);

      RegularizedModel model = Ridge.fit(x, t, 0.0);

      assertDoubles(List.of(2.0), model.coefficients(), 1e-12);
      assertThat(model.intercept()).isCloseTo(1.0, within(1e-12));
    }

    @Test
    @DisplayName("特徴量が 2 つでも係数と切片を求める")
    void twoFeatures() {
      var x = Matrix.of(new double[][] {{1, 0}, {0, 1}, {1, 1}, {2, 1}});
      var t = List.of(7.0, 3.0, 6.0, 9.0);

      RegularizedModel model = Ridge.fit(x, t, 0.0);

      assertDoubles(List.of(3.0, -1.0), model.coefficients(), 1e-9);
      assertThat(model.intercept()).isCloseTo(4.0, within(1e-9));
    }

    @Test
    @DisplayName("alpha を大きくすると係数の絶対値の合計が小さくなる")
    void shrinks() {
      var data = Samples.randomDataset();

      var weak = Ridge.fit(data.x(), data.t(), 0.1);
      var strong = Ridge.fit(data.x(), data.t(), 100.0);

      assertThat(strong.coefficientAbsSum()).isLessThan(weak.coefficientAbsSum());
    }

    @Test
    @DisplayName("alpha が 0 なら第 7 章の線形回帰と同じ係数と切片になる")
    void sameAsChapter07() {
      var data = Samples.randomDataset();
      List<String> names = List.of("x0", "x1", "x2", "x3");
      List<Features> features =
          Arrays.stream(data.x().toArray()).map(row -> new Features(names, row)).toList();

      RegularizedModel model = Ridge.fit(data.x(), data.t(), 0.0);
      LinearModel expected = LinearRegression.fit(features, data.t());

      assertDoubles(new ArrayList<>(expected.coefficients().values()), model.coefficients(), 1e-9);
      assertThat(model.intercept()).isCloseTo(expected.intercept(), within(1e-9));
    }

    @Test
    @DisplayName("特徴量と正解の件数が違えばエラーになる")
    void sizeMismatch() {
      var x = Matrix.of(new double[][] {{1}, {2}});

      assertThatThrownBy(() -> Ridge.fit(x, List.of(1.0), 0.0))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Nested
  @DisplayName("予測")
  class PredictTest {
    @Test
    @DisplayName("係数と切片から予測値を計算する")
    void predict() {
      var model = new RegularizedModel(List.of(3.0, -1.0), 4.0);

      List<Double> y = model.predict(Matrix.of(new double[][] {{1, 2}, {0, 0}}));

      assertDoubles(List.of(5.0, 4.0), y, 1e-12);
    }

    @Test
    @DisplayName("特徴量の列数と係数の数が違えばエラーになる")
    void columnMismatch() {
      var model = new RegularizedModel(List.of(3.0, -1.0), 4.0);

      assertThatThrownBy(() -> model.predict(Matrix.of(new double[][] {{1}})))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("特徴量の列数 1 と係数の数 2 が違います");
    }
  }
}
