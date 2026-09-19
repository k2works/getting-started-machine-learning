package chapter07;

import java.util.Arrays;

/**
 * 変更できない行列。第 11〜13 章でも使う。
 *
 * <p>値は double の 2 次元配列で持つ。受け取るときも返すときも配列を写すので、外から中身を変えられない。
 */
public final class Matrix {
  private final double[][] values;

  private Matrix(double[][] values) {
    this.values = values;
  }

  /** 行の配列から行列を作る。どの行も同じ長さでなければならない。 */
  public static Matrix of(double[][] rows) {
    if (rows.length == 0 || rows[0].length == 0) {
      throw new IllegalArgumentException("行列は 1 行 1 列以上でなければなりません");
    }
    double[][] copy = new double[rows.length][];
    for (int i = 0; i < rows.length; i++) {
      if (rows[i].length != rows[0].length) {
        throw new IllegalArgumentException("行によって列数が違います");
      }
      copy[i] = rows[i].clone();
    }
    return new Matrix(copy);
  }

  /** 値を縦に並べた 1 列の行列（列ベクトル）を作る。 */
  public static Matrix columnVector(double... values) {
    double[][] rows = new double[values.length][];
    for (int i = 0; i < values.length; i++) {
      rows[i] = new double[] {values[i]};
    }
    return of(rows);
  }

  public int rowCount() {
    return values.length;
  }

  public int columnCount() {
    return values[0].length;
  }

  /** i 行 j 列の値（0 始まり）。 */
  public double get(int i, int j) {
    return values[i][j];
  }

  /** j 列目の値の写し。 */
  public double[] column(int j) {
    double[] column = new double[rowCount()];
    for (int i = 0; i < rowCount(); i++) {
      column[i] = values[i][j];
    }
    return column;
  }

  /** 行の配列の写し。 */
  public double[][] toArray() {
    return Arrays.stream(values).map(double[]::clone).toArray(double[][]::new);
  }

  /** 行列の積。左の列数と右の行数が同じでなければならない。 */
  public Matrix times(Matrix other) {
    if (columnCount() != other.rowCount()) {
      throw new IllegalArgumentException(
          "左の行列の列数 " + columnCount() + " と右の行列の行数 " + other.rowCount() + " が違います");
    }
    double[][] product = new double[rowCount()][other.columnCount()];
    for (int i = 0; i < rowCount(); i++) {
      for (int j = 0; j < other.columnCount(); j++) {
        double sum = 0;
        for (int k = 0; k < columnCount(); k++) {
          sum += values[i][k] * other.values[k][j];
        }
        product[i][j] = sum;
      }
    }
    return new Matrix(product);
  }

  /** 行と列を入れ替えた行列。 */
  public Matrix transpose() {
    double[][] transposed = new double[columnCount()][];
    for (int j = 0; j < columnCount(); j++) {
      transposed[j] = column(j);
    }
    return new Matrix(transposed);
  }

  /** 正方行列 A について、A x = b を満たす列ベクトル x を部分ピボット選択つきのガウスの消去法で求める。 */
  public Matrix solve(Matrix b) {
    int n = rowCount();
    double[][] augmented = new double[n][];
    for (int i = 0; i < n; i++) {
      augmented[i] = Arrays.copyOf(values[i], n + 1);
      augmented[i][n] = b.values[i][0];
    }
    for (int pivot = 0; pivot < n; pivot++) {
      swap(augmented, pivot, largestRow(augmented, pivot));
      for (int i = pivot + 1; i < n; i++) {
        double factor = augmented[i][pivot] / augmented[pivot][pivot];
        for (int j = pivot; j <= n; j++) {
          augmented[i][j] -= factor * augmented[pivot][j];
        }
      }
    }
    double[] x = new double[n];
    for (int i = n - 1; i >= 0; i--) {
      double known = 0;
      for (int j = i + 1; j < n; j++) {
        known += augmented[i][j] * x[j];
      }
      x[i] = (augmented[i][n] - known) / augmented[i][i];
    }
    return columnVector(x);
  }

  private static int largestRow(double[][] augmented, int pivot) {
    int largest = pivot;
    for (int i = pivot + 1; i < augmented.length; i++) {
      if (Math.abs(augmented[i][pivot]) > Math.abs(augmented[largest][pivot])) {
        largest = i;
      }
    }
    return largest;
  }

  private static void swap(double[][] rows, int i, int j) {
    double[] row = rows[i];
    rows[i] = rows[j];
    rows[j] = row;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Matrix that && Arrays.deepEquals(values, that.values);
  }

  @Override
  public int hashCode() {
    return Arrays.deepHashCode(values);
  }

  @Override
  public String toString() {
    return "Matrix" + Arrays.deepToString(values);
  }
}
