package chapter10;

import static chapter10.Samples.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LogisticRegressionTest {
  @Nested
  class Softmax {
    @Test
    @DisplayName("値がすべて同じなら確率は均等になる")
    void uniform() {
      assertThat(LogisticRegression.softmax(new double[] {0.0, 0.0, 0.0, 0.0}))
          .containsExactly(0.25, 0.25, 0.25, 0.25);
    }

    @Test
    @DisplayName("値の差が指数の比になる")
    void ratio() {
      double[] probabilities = LogisticRegression.softmax(new double[] {0.0, Math.log(2.0)});

      assertThat(probabilities[0]).isCloseTo(1.0 / 3, within(1e-12));
      assertThat(probabilities[1]).isCloseTo(2.0 / 3, within(1e-12));
    }

    @Test
    @DisplayName("大きな値でもあふれずに確率を求める")
    void largeValues() {
      assertThat(LogisticRegression.softmax(new double[] {1000.0, 1000.0}))
          .containsExactly(0.5, 0.5);
    }
  }

  @Nested
  class FitAndPredict {
    @Test
    @DisplayName("1 種類のラベルだけを学習するとそのラベルを予測する")
    void singleLabel() {
      var model =
          new LogisticRegression().fit(column("花弁幅", 0.1, 0.2), List.of("setosa", "setosa"));

      assertThat(model.predict(column("花弁幅", 0.15, 0.9))).containsExactly("setosa", "setosa");
    }

    @Test
    @DisplayName("2 種類のラベルを境界の左右で予測する")
    void twoLabels() {
      var model =
          new LogisticRegression()
              .fit(
                  column("花弁幅", 0.1, 0.2, 0.8, 0.9),
                  List.of("setosa", "setosa", "virginica", "virginica"));

      assertThat(model.predict(column("花弁幅", 0.15, 0.85))).containsExactly("setosa", "virginica");
    }

    @Test
    @DisplayName("3 種類のラベルを 2 つの特徴量から予測する")
    void threeLabels() {
      var x =
          Samples.columns(
              "花弁長さ",
              new double[] {0.1, 0.2, 0.5, 0.6, 0.5, 0.6},
              "花弁幅",
              new double[] {0.1, 0.2, 0.1, 0.2, 0.8, 0.9});
      var t = List.of("setosa", "setosa", "versicolor", "versicolor", "virginica", "virginica");

      var model = new LogisticRegression().fit(x, t);

      assertThat(model.predict(x)).isEqualTo(t);
    }

    @Test
    @DisplayName("学習を繰り返すと損失が小さくなる")
    void lossDecreases() {
      var model =
          new LogisticRegression(1.0, 100)
              .fit(
                  column("花弁幅", 0.1, 0.2, 0.8, 0.9),
                  List.of("setosa", "setosa", "virginica", "virginica"));

      assertThat(model.losses()).hasSize(100);
      assertThat(model.losses().getLast()).isLessThan(model.losses().getFirst());
    }

    @Test
    @DisplayName("学習する前に予測するとエラーになる")
    void predictBeforeFit() {
      assertThatThrownBy(() -> new LogisticRegression().predict(column("花弁幅", 0.1)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("fit で学習してから predict を呼んでください");
    }
  }
}
