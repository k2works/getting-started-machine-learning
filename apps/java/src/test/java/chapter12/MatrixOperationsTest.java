package chapter12;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chapter07.Matrix;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MatrixOperationsTest {
  @Test
  @DisplayName("同じ大きさの行列を成分ごとに足す")
  void plus() {
    var a = Matrix.of(new double[][] {{1, 2}, {3, 4}});
    var b = Matrix.of(new double[][] {{10, 20}, {30, 40}});

    assertThat(MatrixOperations.plus(a, b))
        .isEqualTo(Matrix.of(new double[][] {{11, 22}, {33, 44}}));
  }

  @Test
  @DisplayName("大きさが違う行列は足せない")
  void plusMismatch() {
    var a = Matrix.of(new double[][] {{1, 2}});
    var b = Matrix.of(new double[][] {{1}, {2}});

    assertThatThrownBy(() -> MatrixOperations.plus(a, b))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("1 行 2 列の行列と 2 行 1 列の行列は足せません");
  }

  @Test
  @DisplayName("数と行列の積はすべての成分を数倍する")
  void times() {
    var a = Matrix.of(new double[][] {{1, -2}, {0, 3}});

    assertThat(MatrixOperations.times(2.0, a))
        .isEqualTo(Matrix.of(new double[][] {{2, -4}, {0, 6}}));
  }

  @Test
  @DisplayName("単位行列は対角成分が 1 でほかが 0")
  void identity() {
    assertThat(MatrixOperations.identity(3))
        .isEqualTo(Matrix.of(new double[][] {{1, 0, 0}, {0, 1, 0}, {0, 0, 1}}));
  }

  @Test
  @DisplayName("足し算と数倍は元の行列を変えない")
  void immutable() {
    var a = Matrix.of(new double[][] {{1, 2}});

    MatrixOperations.plus(a, a);
    MatrixOperations.times(3.0, a);

    assertThat(a).isEqualTo(Matrix.of(new double[][] {{1, 2}}));
  }
}
