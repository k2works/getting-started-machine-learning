package chapter12;

import static org.assertj.core.api.Assertions.assertThat;

import chapter07.Matrix;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ModelSelectionTest {
  private static Matrix rows(Matrix x, int from, int to) {
    return Matrix.of(Arrays.copyOfRange(x.toArray(), from, to));
  }

  @Test
  @DisplayName("正則化の強さごとに 1 件ずつ実験結果を記録する")
  void oneExperimentPerAlpha() {
    var data = Samples.randomDataset();

    List<Experiment> experiments =
        ModelSelection.runRidgeExperiments(
            rows(data.x(), 0, 20),
            data.t().subList(0, 20),
            rows(data.x(), 20, 30),
            data.t().subList(20, 30),
            List.of(0.1, 1.0, 10.0));

    assertThat(experiments).extracting(Experiment::alpha).containsExactly(0.1, 1.0, 10.0);
  }

  private static Experiment experiment(double alpha, double validationScore) {
    return new Experiment(alpha, 0.9, validationScore, 1.0);
  }

  @Test
  @DisplayName("検証データの決定係数が最も高い実験を選ぶ")
  void best() {
    var experiments = List.of(experiment(0.1, 0.7), experiment(1.0, 0.6));

    assertThat(ModelSelection.bestExperiment(experiments).alpha()).isEqualTo(0.1);
  }

  @Test
  @DisplayName("最も高い実験が途中にあってもそれを選ぶ")
  void bestInTheMiddle() {
    var experiments = List.of(experiment(0.1, 0.5), experiment(1.0, 0.8), experiment(10.0, 0.6));

    assertThat(ModelSelection.bestExperiment(experiments).alpha()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("検証データの決定係数が同じなら先の実験を選ぶ")
  void tie() {
    var experiments = List.of(experiment(0.1, 0.8), experiment(1.0, 0.8));

    assertThat(ModelSelection.bestExperiment(experiments).alpha()).isEqualTo(0.1);
  }

  @Test
  @DisplayName("0 になった係数の特徴量名を返す")
  void zeroCoefficientNames() {
    assertThat(
            ModelSelection.zeroCoefficientNames(
                List.of(0.0, 1.5, 0.0), List.of("RM", "LSTAT", "RM^2")))
        .containsExactly("RM", "RM^2");
  }
}
