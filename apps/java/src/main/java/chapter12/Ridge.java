package chapter12;

import chapter07.Matrix;
import java.util.Arrays;
import java.util.List;

/** リッジ回帰を閉形式（正規方程式に alpha I を足した連立方程式）で解く。 */
public final class Ridge {
  private Ridge() {}

  /**
   * 特徴量と正解から平均を引いてから (Xᵀ X + alpha I) w = Xᵀ t を解いて係数を求め、切片は平均値から求める。
   *
   * <p>平均を引くのは、切片に罰則をかけないため。alpha が 0 なら最小二乗法と同じ解になる。
   */
  public static RegularizedModel fit(Matrix x, List<Double> t, double alpha) {
    if (x.rowCount() != t.size()) {
      throw new IllegalArgumentException("特徴量と正解の件数が違います");
    }
    double[] xMeans = columnMeans(x);
    double tMean = t.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    double[][] centered = x.toArray();
    for (double[] row : centered) {
      for (int j = 0; j < row.length; j++) {
        row[j] -= xMeans[j];
      }
    }
    Matrix xc = Matrix.of(centered);
    Matrix tc = Matrix.columnVector(t.stream().mapToDouble(v -> v - tMean).toArray());
    Matrix xct = xc.transpose();
    Matrix penalized =
        MatrixOperations.plus(
            xct.times(xc), MatrixOperations.times(alpha, MatrixOperations.identity(xMeans.length)));
    double[] coefficients = penalized.solve(xct.times(tc)).column(0);
    double intercept = tMean;
    for (int j = 0; j < coefficients.length; j++) {
      intercept -= xMeans[j] * coefficients[j];
    }
    return new RegularizedModel(Arrays.stream(coefficients).boxed().toList(), intercept);
  }

  static double[] columnMeans(Matrix x) {
    double[] means = new double[x.columnCount()];
    for (int j = 0; j < means.length; j++) {
      means[j] = Arrays.stream(x.column(j)).average().orElseThrow();
    }
    return means;
  }
}
