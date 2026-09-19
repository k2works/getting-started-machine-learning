package chapter09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import chapter02.Features;
import chapter02.TrainTestSplit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LinearModelTest {
  @Test
  @DisplayName("正規方程式を解いて切片と係数を求める")
  void fitsNormalEquation() {
    double[][] rows = {{1, 0}, {0, 1}, {1, 1}, {2, 3}};
    List<Double> t = List.of(rows).stream().map(r -> 2 + 3 * r[0] - r[1]).toList();

    LinearModel model = LinearModel.fit(rows, t);

    assertThat(model.intercept()).isCloseTo(2.0, within(1e-9));
    assertThat(model.weights().get(0)).isCloseTo(3.0, within(1e-9));
    assertThat(model.weights().get(1)).isCloseTo(-1.0, within(1e-9));
  }

  @Test
  @DisplayName("予測がすべて当たれば決定係数は 1、平均を返すだけなら 0")
  void rSquared() {
    var actual = List.of(1.0, 2.0, 3.0);

    assertThat(LinearModel.rSquared(actual, actual)).isCloseTo(1.0, within(1e-12));
    assertThat(LinearModel.rSquared(actual, List.of(2.0, 2.0, 2.0))).isCloseTo(0.0, within(1e-12));
  }

  private static double price(double rm) {
    return 3 * rm * rm + 1;
  }

  private static TrainTestSplit<Features, Double> quadraticSplit() {
    var columns = List.of("RM", "LSTAT");
    List<Double> trainRm = List.of(1.0, 2.0, 3.0, 4.0);
    List<Double> trainLstat = List.of(9.0, 7.0, 8.0, 6.0);
    List<Features> xTrain =
        java.util.stream.IntStream.range(0, trainRm.size())
            .mapToObj(i -> new Features(columns, new double[] {trainRm.get(i), trainLstat.get(i)}))
            .toList();
    List<Features> xTest =
        List.of(
            new Features(columns, new double[] {5, 5}), new Features(columns, new double[] {6, 4}));
    return new TrainTestSplit<>(
        xTrain,
        xTest,
        trainRm.stream().map(LinearModelTest::price).toList(),
        List.of(price(5), price(6)));
  }

  @Test
  @DisplayName("2 乗の項が無いと 2 次式の価格を当てきれない")
  void withoutSquareCannotFit() {
    Scores scores = Boston.scoreFeatureSet(quadraticSplit(), List.of("RM"), List.of("RM"));

    assertThat(scores.train()).isLessThan(1.0);
  }

  @Test
  @DisplayName("2 乗の項を加えると 2 次式の価格を当てられる")
  void withSquareFits() {
    Scores scores = Boston.scoreFeatureSet(quadraticSplit(), List.of("RM"), List.of("RM", "RM^2"));

    assertThat(scores.train()).isCloseTo(1.0, within(1e-9));
    assertThat(scores.test()).isCloseTo(1.0, within(1e-9));
  }
}
