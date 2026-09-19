package chapter07;

import java.util.List;
import java.util.stream.IntStream;

/** 回帰の評価指標。t は実測値、y は予測値。第 11・12 章でも使う。 */
public final class RegressionMetrics {
  private RegressionMetrics() {}

  private static double[] residuals(List<Double> t, List<Double> y) {
    if (t.size() != y.size()) {
      throw new IllegalArgumentException("実測値と予測値の件数が違います");
    }
    return IntStream.range(0, t.size()).mapToDouble(i -> t.get(i) - y.get(i)).toArray();
  }

  /** 平均絶対誤差（MAE）。誤差の絶対値の平均。 */
  public static double meanAbsoluteError(List<Double> t, List<Double> y) {
    double sum = 0;
    for (double residual : residuals(t, y)) {
      sum += Math.abs(residual);
    }
    return sum / t.size();
  }

  /** 平均二乗誤差の平方根（RMSE）。 */
  public static double rootMeanSquaredError(List<Double> t, List<Double> y) {
    return Math.sqrt(sumOfSquares(residuals(t, y)) / t.size());
  }

  /** 決定係数（R²）。1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。 */
  public static double r2Score(List<Double> t, List<Double> y) {
    double residual = sumOfSquares(residuals(t, y));
    double mean = t.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    double total = sumOfSquares(t.stream().mapToDouble(value -> value - mean).toArray());
    return 1 - residual / total;
  }

  private static double sumOfSquares(double[] values) {
    double sum = 0;
    for (double value : values) {
      sum += value * value;
    }
    return sum;
  }
}
