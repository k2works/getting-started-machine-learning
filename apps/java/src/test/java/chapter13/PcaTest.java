package chapter13;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import chapter07.Matrix;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PcaTest {
  private static final double TOLERANCE = 1e-9;

  private static void assertMatrixEquals(double[][] expected, Matrix actual) {
    assertThat(actual.rowCount()).isEqualTo(expected.length);
    for (int i = 0; i < expected.length; i++) {
      assertThat(actual.columnCount()).isEqualTo(expected[i].length);
      for (int j = 0; j < expected[i].length; j++) {
        assertThat(actual.get(i, j)).isCloseTo(expected[i][j], within(TOLERANCE));
      }
    }
  }

  @Test
  @DisplayName("2 列の分散と共分散を並べた行列を返す")
  void covarianceOfTwoColumns() {
    Matrix x = Matrix.of(new double[][] {{1, 2}, {3, 6}, {5, 10}});

    assertMatrixEquals(new double[][] {{4, 8}, {8, 16}}, Pca.covarianceMatrix(x));
  }

  @Test
  @DisplayName("3 列でも各列の分散と 2 列ずつの共分散を並べる")
  void covarianceOfThreeColumns() {
    Matrix x = Matrix.of(new double[][] {{1, 2, 0}, {3, 6, 1}, {5, 10, 5}});

    assertMatrixEquals(
        new double[][] {{4, 8, 5}, {8, 16, 10}, {5, 10, 7}}, Pca.covarianceMatrix(x));
  }

  @Test
  @DisplayName("完全に相関する 2 列なら第 1 主成分だけで分散をすべて説明する")
  void perfectlyCorrelated() {
    Matrix x = Matrix.of(new double[][] {{1, 2}, {3, 6}, {5, 10}});

    PcaModel model = Pca.fit(x, 2);

    assertMatrixEquals(
        new double[][] {{1 / Math.sqrt(5), 2 / Math.sqrt(5)}},
        Matrix.of(new double[][] {model.components().toArray()[0]}));
    assertThat(model.explainedVarianceRatio())
        .satisfiesExactly(
            r -> assertThat(r).isCloseTo(1.0, within(TOLERANCE)),
            r -> assertThat(r).isCloseTo(0.0, within(TOLERANCE)));
  }

  /** 2 つの隠れた変数を 4 列に混ぜ、小さなノイズを加えた 40 行の架空のデータ。 */
  private static Matrix mixedDataset() {
    Random random = new Random(0);
    double[][] mixing = {{2.0, 0.5}, {0.3, 1.0}, {1.0, -1.0}, {0.0, 0.2}};
    double[][] rows = new double[40][];
    for (int i = 0; i < rows.length; i++) {
      double[] base = {random.nextGaussian(), random.nextGaussian()};
      rows[i] = new double[mixing.length];
      for (int j = 0; j < mixing.length; j++) {
        rows[i][j] = mixing[j][0] * base[0] + mixing[j][1] * base[1] + random.nextGaussian() * 0.1;
      }
    }
    return Matrix.of(rows);
  }

  @Test
  @DisplayName("主成分は寄与率の大きい順に指定した数だけ並ぶ")
  void sortedByRatio() {
    PcaModel model = Pca.fit(mixedDataset(), 3);

    List<Double> ratios = model.explainedVarianceRatio();
    assertThat(ratios).hasSize(3).isSortedAccordingTo(Comparator.reverseOrder());
  }

  @Test
  @DisplayName("主成分の向きは絶対値が最大の要素が正になるようにそろえる")
  void normalizesSign() {
    Matrix x = Matrix.of(new double[][] {{1, 2}, {3, 6}, {5, 10}});

    PcaModel model = Pca.fit(x, 2);

    assertMatrixEquals(
        new double[][] {{2 / Math.sqrt(5), -1 / Math.sqrt(5)}},
        Matrix.of(new double[][] {model.components().toArray()[1]}));
  }

  @Test
  @DisplayName("絶対値が最大の要素が正になるように主成分の向きをそろえる")
  void normalizeSigns() {
    Matrix components = Matrix.of(new double[][] {{0.6, -0.8}, {-0.8, 0.6}});

    assertMatrixEquals(new double[][] {{-0.6, 0.8}, {0.8, -0.6}}, Pca.normalizeSigns(components));
  }

  @Test
  @DisplayName("主成分は長さ 1 で互いに直交する")
  void orthonormal() {
    PcaModel model = Pca.fit(mixedDataset(), 3);

    Matrix gram = model.components().times(model.components().transpose());

    assertMatrixEquals(new double[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}, gram);
  }

  @Test
  @DisplayName("主成分は分散共分散行列に掛けると分散の倍数になる固有ベクトル")
  void eigenvectorsOfCovariance() {
    Matrix x = mixedDataset();

    PcaModel model = Pca.fit(x, 3);

    Matrix covariance = Pca.covarianceMatrix(x);
    double[][] components = model.components().toArray();
    for (int i = 0; i < components.length; i++) {
      double variance = model.explainedVariance().get(i);
      Matrix projected = covariance.times(Matrix.columnVector(components[i]));
      double[] expected = Arrays.stream(components[i]).map(v -> v * variance).toArray();
      assertThat(projected.column(0)).containsExactly(expected, within(TOLERANCE));
    }
  }

  @Test
  @DisplayName("平均を引いてから主成分の向きに射影する")
  void transform() {
    var model =
        new PcaModel(
            List.of(1.0, 2.0), Matrix.of(new double[][] {{0.6, 0.8}}), List.of(1.0), List.of(1.0));

    assertMatrixEquals(
        new double[][] {{1.4}, {0.0}},
        Pca.transform(model, Matrix.of(new double[][] {{2, 3}, {1, 2}})));
  }

  @Test
  @DisplayName("累積寄与率がしきい値に届くまでの主成分の数を返す")
  void componentsNeeded() {
    assertThat(Pca.componentsNeeded(List.of(0.5, 0.25, 0.25), 0.75)).isEqualTo(2);
  }

  @Test
  @DisplayName("しきい値を上げると必要な主成分の数が増える")
  void componentsNeededGrows() {
    assertThat(Pca.componentsNeeded(List.of(0.5, 0.25, 0.25), 0.8)).isEqualTo(3);
  }

  @Test
  @DisplayName("係数の絶対値が大きい順に列名と係数を返す")
  void topLoadings() {
    double[] component = {0.1, -0.7, 0.5};

    assertThat(Pca.topLoadings(component, List.of("ZN", "DIS", "TAX"), 2))
        .containsExactly(new Loading("DIS", -0.7), new Loading("TAX", 0.5));
  }
}
