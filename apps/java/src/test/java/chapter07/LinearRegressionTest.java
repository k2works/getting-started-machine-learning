package chapter07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import chapter02.Features;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LinearRegressionTest {
  /** 列名と、行ごとの値から特徴量のリストを作る。 */
  static List<Features> rows(List<String> columns, double[]... values) {
    List<Features> x = new ArrayList<>();
    for (double[] row : values) {
      x.add(new Features(columns, row));
    }
    return x;
  }

  private static void assertModel(
      double intercept, List<String> names, double[] coefficients, LinearModel actual) {
    assertThat(actual.intercept()).isCloseTo(intercept, within(1e-9));
    assertThat(actual.coefficients().keySet()).containsExactlyElementsOf(names);
    for (int i = 0; i < names.size(); i++) {
      assertThat(actual.coefficients().get(names.get(i))).isCloseTo(coefficients[i], within(1e-9));
    }
  }

  @Nested
  @DisplayName("学習")
  class Fit {
    @Test
    @DisplayName("直線上の点から切片と係数を求める")
    void line() {
      var x =
          rows(
              List.of("x"), new double[] {0}, new double[] {1}, new double[] {2}, new double[] {3});
      var t = List.of(1.0, 3.0, 5.0, 7.0);

      var model = LinearRegression.fit(x, t);

      assertModel(1.0, List.of("x"), new double[] {2}, model);
    }

    @Test
    @DisplayName("複数の特徴量から切片と係数を求める")
    void plane() {
      double[][] ab = {{0, 0}, {1, 0}, {0, 1}, {2, 1}, {1, 3}};
      var x = rows(List.of("a", "b"), ab);
      List<Double> t = new ArrayList<>();
      for (double[] row : ab) {
        t.add(3 * row[0] - 2 * row[1] + 5);
      }

      var model = LinearRegression.fit(x, t);

      assertModel(5.0, List.of("a", "b"), new double[] {3, -2}, model);
    }
  }

  @Nested
  @DisplayName("予測")
  class Predict {
    private final LinearModel model = LinearModel.of(1.0, List.of("a", "b"), new double[] {2, -1});

    @Test
    @DisplayName("切片と係数から予測値を計算する")
    void predict() {
      var x = rows(List.of("a", "b"), new double[] {1, 4}, new double[] {3, 0.5});

      assertThat(model.predict(x)).containsExactly(-1.0, 6.5);
    }

    @Test
    @DisplayName("列の並び順が違っても列名で係数を対応させる")
    void byName() {
      var x = rows(List.of("b", "a"), new double[] {4, 1}, new double[] {0.5, 3});

      assertThat(model.predict(x)).containsExactly(-1.0, 6.5);
    }
  }
}
