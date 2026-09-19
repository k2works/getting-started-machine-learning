package chapter12;

import static chapter12.Samples.assertDoubles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import chapter07.Matrix;
import com.oracle.labs.mlrg.olcut.config.PropertyException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tribuo.regression.slm.ElasticNetCDTrainer;

class TribuoRegularizationTest {
  /** 4 列のうち、最初の 2 列だけで正解が決まる人工データ */
  private static Samples.Data sparseDataset() {
    var random = new Random(0);
    double[][] rows = new double[50][4];
    List<Double> t = new ArrayList<>();
    for (double[] row : rows) {
      for (int j = 0; j < row.length; j++) {
        row[j] = random.nextGaussian();
      }
      t.add(3.0 * row[0] - 2.0 * row[1] + 0.1 * random.nextGaussian());
    }
    return new Samples.Data(Matrix.of(rows), t);
  }

  @Test
  @DisplayName("ラッソ回帰では予測に役立たない特徴量の係数が 0 になる")
  void lasso() {
    var data = sparseDataset();

    RegularizedModel model = TribuoRegularization.fitLasso(data.x(), data.t(), 0.5);

    assertThat(
            ModelSelection.zeroCoefficientNames(
                model.coefficients(), List.of("x1", "x2", "noise1", "noise2")))
        .containsExactly("noise1", "noise2");
  }

  @Test
  @DisplayName("ElasticNetCDTrainer は l1Ratio が 0 のリッジ回帰を受け付けない")
  void rejectsZeroL1Ratio() {
    assertThatThrownBy(() -> new ElasticNetCDTrainer(0.5, 0.0))
        .isInstanceOf(PropertyException.class)
        .hasMessageEndingWith("L1 Ratio must be between 0 and 1. Found value 0.0");
  }

  @Test
  @DisplayName("ElasticNetCDTrainer は l1Ratio の下限の 1e-12 を受け付け、1e-13 を受け付けない")
  void l1RatioLowerBound() {
    assertThat(new ElasticNetCDTrainer(0.5, TribuoRegularization.MIN_L1_RATIO)).isNotNull();
    assertThatThrownBy(() -> new ElasticNetCDTrainer(0.5, 1e-13))
        .isInstanceOf(PropertyException.class);
  }

  @Test
  @DisplayName("l1Ratio を下限まで小さくし alpha を件数で割ると自作のリッジ回帰と同じ係数と切片になる")
  void ridge() {
    var data = Samples.randomDataset();

    RegularizedModel model = TribuoRegularization.fitRidge(data.x(), data.t(), 10.0);
    RegularizedModel expected = Ridge.fit(data.x(), data.t(), 10.0);

    assertDoubles(expected.coefficients(), model.coefficients(), 1e-6);
    assertThat(model.intercept()).isCloseTo(expected.intercept(), within(1e-6));
  }
}
