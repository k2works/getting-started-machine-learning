package chapter09;

import java.util.Arrays;
import java.util.List;
import org.tribuo.math.la.DenseMatrix;
import org.tribuo.math.la.DenseVector;

/**
 * 線形回帰のモデル。正規方程式を Tribuo の行列（コレスキー分解）で解いて求める。
 *
 * @param intercept 切片
 * @param weights 特徴量の列ごとの係数
 */
public record LinearModel(double intercept, List<Double> weights) {
  public LinearModel {
    weights = List.copyOf(weights);
  }

  /** 1 行の特徴量から予測する。 */
  public double predict(double[] row) {
    double sum = intercept;
    for (int i = 0; i < row.length; i++) {
      sum += weights.get(i) * row[i];
    }
    return sum;
  }

  /** 行ごとに予測する。 */
  public List<Double> predict(double[][] rows) {
    return Arrays.stream(rows).map(this::predict).toList();
  }

  /** 先頭に 1 の列を加えた計画行列 X で、正規方程式 XᵀX β = Xᵀt を解く。 */
  public static LinearModel fit(double[][] rows, List<Double> t) {
    double[][] design = new double[rows.length][];
    for (int i = 0; i < rows.length; i++) {
      design[i] = new double[rows[i].length + 1];
      design[i][0] = 1;
      System.arraycopy(rows[i], 0, design[i], 1, rows[i].length);
    }
    DenseMatrix x = DenseMatrix.createDenseMatrix(design);
    DenseMatrix transposed = x.transpose();
    DenseMatrix.CholeskyFactorization cholesky =
        transposed
            .matrixMultiply(x)
            .choleskyFactorization()
            .orElseThrow(() -> new IllegalArgumentException("特徴量の列が互いに独立でないため、正規方程式を解けません"));
    DenseVector target = DenseVector.createDenseVector(t.stream().mapToDouble(v -> v).toArray());
    double[] beta = cholesky.solve(transposed.leftMultiply(target)).toArray();
    return new LinearModel(beta[0], Arrays.stream(beta).skip(1).boxed().toList());
  }

  /** 決定係数。1 から、残差の 2 乗和を平均との差の 2 乗和で割った値を引く。 */
  public static double rSquared(List<Double> actual, List<Double> predicted) {
    double mean = actual.stream().mapToDouble(v -> v).average().orElseThrow();
    double residual = 0;
    double total = 0;
    for (int i = 0; i < actual.size(); i++) {
      residual += Math.pow(actual.get(i) - predicted.get(i), 2);
      total += Math.pow(actual.get(i) - mean, 2);
    }
    return 1 - residual / total;
  }
}
