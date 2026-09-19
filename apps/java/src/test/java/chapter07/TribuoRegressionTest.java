package chapter07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import chapter02.Features;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tribuo.regression.Regressor;
import org.tribuo.regression.evaluation.RegressionEvaluator;
import org.tribuo.regression.slm.LARSTrainer;
import org.tribuo.regression.slm.SLMTrainer;
import org.tribuo.regression.slm.SparseLinearModel;

class TribuoRegressionTest {
  private static final List<String> COLUMNS = List.of("a", "b", "c");
  private static final int SIZE = 30;

  /** t = 4 + 1.5a - 0.5b + 2c に正規分布のノイズを加えた 30 件の架空のデータ。 */
  private record Noisy(List<Features> x, List<Double> t) {}

  private static Noisy noisyDataset() {
    var random = new Random(0);
    double[][] columns = new double[COLUMNS.size()][SIZE];
    for (double[] column : columns) {
      for (int i = 0; i < SIZE; i++) {
        column[i] = random.nextDouble() * 10;
      }
    }
    List<Features> x = new ArrayList<>();
    List<Double> t = new ArrayList<>();
    for (int i = 0; i < SIZE; i++) {
      double[] row = {columns[0][i], columns[1][i], columns[2][i]};
      x.add(new Features(COLUMNS, row));
      t.add(4 + 1.5 * row[0] - 0.5 * row[1] + 2 * row[2] + random.nextGaussian());
    }
    return new Noisy(x, t);
  }

  /** 平均との差の 2 乗の合計の平方根。 */
  private static double centeredNorm(double[] values) {
    double mean = java.util.Arrays.stream(values).average().orElseThrow();
    return Math.sqrt(java.util.Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum());
  }

  @Test
  @DisplayName("特徴量の行を数値の正解ラベル付きの事例に変換する")
  void toDataset() {
    var columns = List.of("SNS1", "actor");
    var x =
        List.of(
            new Features(columns, new double[] {100, 9000}),
            new Features(columns, new double[] {200, 9500}));

    var dataset = TribuoRegression.toDataset(x, List.of(9200.0, 9800.0));

    assertThat(dataset.size()).isEqualTo(2);
    assertThat(dataset.getFeatureIDMap().keySet()).containsExactlyInAnyOrder("SNS1", "actor");
    assertThat(dataset.getData())
        .extracting(example -> example.getOutput().getValues()[0])
        .containsExactly(9200.0, 9800.0);
  }

  @Test
  @DisplayName("SLMTrainer(true) の予測は自作の線形回帰の予測と一致する")
  void slmMatchesMine() {
    var data = noisyDataset();

    var model = TribuoRegression.train(new SLMTrainer(true), data.x(), data.t());

    assertThat(TribuoRegression.predict(model, data.x()))
        .zipSatisfy(
            LinearRegression.fit(data.x(), data.t()).predict(data.x()),
            (tribuo, mine) -> assertThat(tribuo).isCloseTo(mine, within(1e-9)));
  }

  @Test
  @DisplayName("LARSTrainer の予測も自作の線形回帰の予測と一致する")
  void larsMatchesMine() {
    var data = noisyDataset();

    var model = TribuoRegression.train(new LARSTrainer(), data.x(), data.t());

    assertThat(TribuoRegression.predict(model, data.x()))
        .zipSatisfy(
            LinearRegression.fit(data.x(), data.t()).predict(data.x()),
            (tribuo, mine) -> assertThat(tribuo).isCloseTo(mine, within(1e-9)));
  }

  @Test
  @DisplayName("Tribuo の重みは正規化した空間の値で、元の単位に戻すと自作の係数と一致する")
  void weightsInNormalizedSpace() {
    var data = noisyDataset();
    var model =
        (SparseLinearModel) TribuoRegression.train(new SLMTrainer(true), data.x(), data.t());
    var weights = model.getWeights().values().iterator().next();
    double[] t = data.t().stream().mapToDouble(Double::doubleValue).toArray();

    var coefficients = LinearRegression.fit(data.x(), data.t()).coefficients();

    for (String name : COLUMNS) {
      double[] column = data.x().stream().mapToDouble(row -> row.value(name)).toArray();
      double weight = weights.get(model.getFeatureIDMap().getID(name));
      assertThat(weight).isNotCloseTo(coefficients.get(name), within(1e-3));
      assertThat(weight * centeredNorm(t) / centeredNorm(column))
          .isCloseTo(coefficients.get(name), within(1e-9));
    }
  }

  @Test
  @DisplayName("Tribuo の評価器の MAE・RMSE・R² は自作の評価指標と一致する")
  void evaluatorMatchesMine() {
    var data = noisyDataset();
    var model = TribuoRegression.train(new SLMTrainer(true), data.x(), data.t());
    var y = TribuoRegression.predict(model, data.x());

    var evaluation =
        new RegressionEvaluator().evaluate(model, TribuoRegression.toDataset(data.x(), data.t()));

    var target = new Regressor(TribuoRegression.OUTPUT_NAME, Double.NaN);
    assertThat(evaluation.mae(target))
        .isCloseTo(RegressionMetrics.meanAbsoluteError(data.t(), y), within(1e-9));
    assertThat(evaluation.rmse(target))
        .isCloseTo(RegressionMetrics.rootMeanSquaredError(data.t(), y), within(1e-9));
    assertThat(evaluation.r2(target))
        .isCloseTo(RegressionMetrics.r2Score(data.t(), y), within(1e-9));
  }
}
