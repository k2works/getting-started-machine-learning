package chapter13;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tribuo.math.la.DenseMatrix;

class TribuoEigenLearningTest {
  private final DenseMatrix symmetric =
      DenseMatrix.createDenseMatrix(new double[][] {{2, 1}, {1, 2}});

  private static double[] rounded(double[] values) {
    return Arrays.stream(values).map(v -> Math.round(v * 1e9) / 1e9).toArray();
  }

  @Test
  @DisplayName("対称行列の固有値を大きい順に返す")
  void eigenvaluesDescending() {
    var eigen = symmetric.eigenDecomposition().orElseThrow();

    assertThat(rounded(eigen.eigenvalues().toArray())).containsExactly(3.0, 1.0);
  }

  @Test
  @DisplayName("対角成分の並びに関係なく固有値を大きい順に並べ替える")
  void sortsEigenvalues() {
    var diagonal = DenseMatrix.createDenseMatrix(new double[][] {{1, 0, 0}, {0, 5, 0}, {0, 0, 3}});

    var eigen = diagonal.eigenDecomposition().orElseThrow();

    assertThat(rounded(eigen.eigenvalues().toArray())).containsExactly(5.0, 3.0, 1.0);
  }

  @Test
  @DisplayName("i 番目の固有ベクトルは行列を掛けても向きが変わらず i 番目の固有値倍になる")
  void eigenvectorMatchesEigenvalue() {
    var eigen = symmetric.eigenDecomposition().orElseThrow();

    for (int i = 0; i < 2; i++) {
      double[] v = eigen.getEigenVector(i).toArray();
      double lambda = eigen.eigenvalues().get(i);
      double[][] a = symmetric.toArray();
      for (int row = 0; row < 2; row++) {
        double av = a[row][0] * v[0] + a[row][1] * v[1];
        assertThat(av).isCloseTo(lambda * v[row], within(1e-9));
      }
    }
  }

  @Test
  @DisplayName("対称でない行列は固有値分解できず空の Optional を返す")
  void asymmetricIsEmpty() {
    var asymmetric = DenseMatrix.createDenseMatrix(new double[][] {{2, 1}, {0, 2}});

    assertThat(asymmetric.eigenDecomposition()).isEmpty();
  }
}
