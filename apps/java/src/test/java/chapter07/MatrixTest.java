package chapter07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class MatrixTest {
  @Nested
  @DisplayName("積")
  class Times {
    @Test
    @DisplayName("行列の積を求める")
    void square() {
      var a = Matrix.of(new double[][] {{1, 2}, {3, 4}});
      var b = Matrix.of(new double[][] {{5, 6}, {7, 8}});

      assertThat(a.times(b)).isEqualTo(Matrix.of(new double[][] {{19, 22}, {43, 50}}));
    }

    @Test
    @DisplayName("行数と列数が違う行列の積を求める")
    void rectangular() {
      var a = Matrix.of(new double[][] {{1, 2, 3}, {4, 5, 6}});
      var b = Matrix.of(new double[][] {{1}, {0}, {2}});

      assertThat(a.times(b)).isEqualTo(Matrix.of(new double[][] {{7}, {16}}));
    }

    @Test
    @DisplayName("左の列数と右の行数が違えば積を求められない")
    void mismatch() {
      var a = Matrix.of(new double[][] {{1, 2}});

      assertThatThrownBy(() -> a.times(a))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("左の行列の列数 2 と右の行列の行数 1 が違います");
    }
  }

  @Nested
  @DisplayName("作成")
  class Create {
    @Test
    @DisplayName("行によって列数が違う配列からは作れない")
    void jagged() {
      assertThatThrownBy(() -> Matrix.of(new double[][] {{1, 2}, {3}}))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("行によって列数が違います");
    }

    @Test
    @DisplayName("作ったあとで元の配列を変えても行列は変わらない")
    void copied() {
      double[][] rows = {{1, 2}};
      var a = Matrix.of(rows);

      rows[0][0] = 9;

      assertThat(a.get(0, 0)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("列ベクトルは値を縦に並べた 1 列の行列になる")
    void columnVector() {
      assertThat(Matrix.columnVector(1, 2)).isEqualTo(Matrix.of(new double[][] {{1}, {2}}));
    }
  }

  @Test
  @DisplayName("行と列を入れ替える")
  void transpose() {
    var a = Matrix.of(new double[][] {{1, 2, 3}, {4, 5, 6}});

    assertThat(a.transpose()).isEqualTo(Matrix.of(new double[][] {{1, 4}, {2, 5}, {3, 6}}));
  }

  @Nested
  @DisplayName("連立方程式")
  class Solve {
    @Test
    @DisplayName("連立方程式の解を求める")
    void twoUnknowns() {
      var a = Matrix.of(new double[][] {{2, 1}, {1, 3}});

      assertThat(a.solve(Matrix.columnVector(3, 5)).column(0))
          .containsExactly(new double[] {0.8, 1.4}, within(1e-9));
    }

    @Test
    @DisplayName("3 元の連立方程式の解を求める")
    void threeUnknowns() {
      var a = Matrix.of(new double[][] {{4, 1, 2}, {1, 3, 0}, {2, 0, 5}});
      var b = a.times(Matrix.columnVector(1, -2, 3));

      assertThat(a.solve(b).column(0)).containsExactly(new double[] {1, -2, 3}, within(1e-9));
    }

    @Test
    @DisplayName("対角成分が 0 でも行を入れ替えて解を求める")
    void zeroPivot() {
      var a = Matrix.of(new double[][] {{0, 1}, {1, 0}});

      assertThat(a.solve(Matrix.columnVector(2, 3)).column(0))
          .containsExactly(new double[] {3, 2}, within(1e-9));
    }
  }
}
