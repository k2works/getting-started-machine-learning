package chapter09;

import chapter02.Features;
import chapter02.TrainTestSplit;
import java.util.List;
import java.util.stream.IntStream;

/** 四分位範囲（IQR）による外れ値の検出。 */
public final class Outliers {
  /** 外れ値とみなす、四分位数から IQR の何倍離れているか */
  public static final double DEFAULT_K = 1.5;

  private static final double FIRST_QUARTILE = 0.25;
  private static final double THIRD_QUARTILE = 0.75;

  private Outliers() {}

  /** 分位数を求める。位置が値の間にあれば前後の値から線形補間する（pandas の quantile の既定と同じ）。 */
  public static double quantile(List<Double> values, double q) {
    List<Double> sorted = values.stream().sorted().toList();
    double position = (sorted.size() - 1) * q;
    int lower = (int) Math.floor(position);
    int upper = (int) Math.ceil(position);
    return sorted.get(lower) + (sorted.get(upper) - sorted.get(lower)) * (position - lower);
  }

  /** 第 1 四分位数から IQR の k 倍より小さい値と、第 3 四分位数から k 倍より大きい値を外れ値とする。 */
  public static List<Boolean> iqrOutliers(List<Double> values, double k) {
    double q1 = quantile(values, FIRST_QUARTILE);
    double q3 = quantile(values, THIRD_QUARTILE);
    double iqr = q3 - q1;
    return values.stream().map(v -> v < q1 - k * iqr || v > q3 + k * iqr).toList();
  }

  /** k を 1.5 にして外れ値を求める。 */
  public static List<Boolean> iqrOutliers(List<Double> values) {
    return iqrOutliers(values, DEFAULT_K);
  }

  /** 訓練データから、正解の値が外れ値の行を取り除く。テストデータはそのまま残す。 */
  public static TrainTestSplit<Features, Double> removeTargetOutliers(
      TrainTestSplit<Features, Double> split) {
    List<Boolean> outliers = iqrOutliers(split.tTrain());
    List<Integer> keep =
        IntStream.range(0, outliers.size()).filter(i -> !outliers.get(i)).boxed().toList();
    return new TrainTestSplit<>(
        keep.stream().map(split.xTrain()::get).toList(),
        split.xTest(),
        keep.stream().map(split.tTrain()::get).toList(),
        split.tTest());
  }
}
