package chapter07;

import chapter02.Features;
import java.util.Arrays;
import java.util.List;

/** 正規方程式で線形回帰を学習する。 */
public final class LinearRegression {
  private LinearRegression() {}

  /** 先頭に 1 の列を足した特徴量の行列（計画行列）を作る。1 の列の係数が切片になる。 */
  static Matrix designMatrix(List<Features> x) {
    double[][] rows = new double[x.size()][];
    for (int i = 0; i < x.size(); i++) {
      double[] values = x.get(i).values();
      rows[i] = new double[values.length + 1];
      rows[i][0] = 1;
      System.arraycopy(values, 0, rows[i], 1, values.length);
    }
    return Matrix.of(rows);
  }

  /** (Xᵀ X) w = Xᵀ t を解いて、切片と係数を求める。列名は先頭の行の特徴量から取る。 */
  public static LinearModel fit(List<Features> x, List<Double> t) {
    Matrix design = designMatrix(x);
    Matrix target = Matrix.columnVector(t.stream().mapToDouble(Double::doubleValue).toArray());
    Matrix transposed = design.transpose();
    double[] weights = transposed.times(design).solve(transposed.times(target)).column(0);
    return LinearModel.of(
        weights[0], x.getFirst().columns(), Arrays.copyOfRange(weights, 1, weights.length));
  }
}
