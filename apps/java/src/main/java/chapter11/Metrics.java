package chapter11;

import java.util.List;
import java.util.function.ToDoubleFunction;

/** 分類と回帰の評価指標。回帰の RMSE・MAE は第 7 章の RegressionMetrics を使う。 */
public final class Metrics {
  private Metrics() {}

  /** 正解と予測の件数が同じでなければ例外を投げる。短いほうに合わせて黙って切り詰めない。 */
  static void requireSameSize(List<?> actual, List<?> predicted) {
    if (actual.size() != predicted.size()) {
      throw new IllegalArgumentException(
          "正解と予測の件数が違います（正解 " + actual.size() + " 件、予測 " + predicted.size() + " 件）");
    }
  }

  private static double ratio(double numerator, double denominator) {
    return denominator == 0 ? 0.0 : numerator / denominator;
  }

  /** 適合率。正例と予測したうち、本当に正例だった割合。 */
  public static double precision(ConfusionMatrix cm) {
    return ratio(cm.tp(), cm.tp() + cm.fp());
  }

  /** 再現率。本当の正例のうち、正例と予測できた割合。 */
  public static double recall(ConfusionMatrix cm) {
    return ratio(cm.tp(), cm.tp() + cm.fn());
  }

  /** F 値。適合率と再現率の調和平均。 */
  public static double f1Score(ConfusionMatrix cm) {
    double p = precision(cm);
    double r = recall(cm);
    return ratio(2 * p * r, p + r);
  }

  /** 平均二乗誤差（MSE）。誤差の 2 乗の平均。 */
  public static double meanSquaredError(List<Double> actual, List<Double> predicted) {
    requireSameSize(actual, predicted);
    double sum = 0;
    for (int i = 0; i < actual.size(); i++) {
      double error = predicted.get(i) - actual.get(i);
      sum += error * error;
    }
    return sum / actual.size();
  }

  /** 正解率。正解と予測が一致した割合。 */
  public static <T> double accuracy(List<T> actual, List<T> predicted) {
    requireSameSize(actual, predicted);
    long hits = 0;
    for (int i = 0; i < actual.size(); i++) {
      if (actual.get(i).equals(predicted.get(i))) {
        hits++;
      }
    }
    return (double) hits / actual.size();
  }

  /** 混同行列から求める指標を、正例を決めて、正解と予測から求める評価関数に変える。 */
  public static <T> Metric<T> classificationMetric(
      ToDoubleFunction<ConfusionMatrix> score, T positive) {
    return (actual, predicted) ->
        score.applyAsDouble(ConfusionMatrix.of(actual, predicted, positive));
  }
}
