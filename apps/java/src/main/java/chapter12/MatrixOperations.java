package chapter12;

import chapter07.Matrix;

/** 第 7 章の Matrix に無い、足し算・定数倍・単位行列。Matrix を変えずに、この章に static メソッドとして足す。 */
public final class MatrixOperations {
  private MatrixOperations() {}

  /** 同じ大きさの行列の和。 */
  public static Matrix plus(Matrix a, Matrix b) {
    if (a.rowCount() != b.rowCount() || a.columnCount() != b.columnCount()) {
      throw new IllegalArgumentException(
          a.rowCount()
              + " 行 "
              + a.columnCount()
              + " 列の行列と "
              + b.rowCount()
              + " 行 "
              + b.columnCount()
              + " 列の行列は足せません");
    }
    double[][] sum = a.toArray();
    for (int i = 0; i < sum.length; i++) {
      for (int j = 0; j < sum[i].length; j++) {
        sum[i][j] += b.get(i, j);
      }
    }
    return Matrix.of(sum);
  }

  /** すべての成分を scalar 倍した行列。 */
  public static Matrix times(double scalar, Matrix matrix) {
    double[][] product = matrix.toArray();
    for (double[] row : product) {
      for (int j = 0; j < row.length; j++) {
        row[j] *= scalar;
      }
    }
    return Matrix.of(product);
  }

  /** size 行 size 列の単位行列。 */
  public static Matrix identity(int size) {
    double[][] rows = new double[size][size];
    for (int i = 0; i < size; i++) {
      rows[i][i] = 1;
    }
    return Matrix.of(rows);
  }
}
