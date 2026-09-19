package chapter12;

import chapter02.Features;
import chapter07.Matrix;
import chapter07.TribuoRegression;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.tribuo.math.la.SparseVector;
import org.tribuo.regression.slm.ElasticNetCDTrainer;
import org.tribuo.regression.slm.SparseLinearModel;

/** Tribuo の ElasticNetCDTrainer で、ラッソ回帰とリッジ回帰を学習する。 */
public final class TribuoRegularization {
  /** ElasticNetCDTrainer が受け付ける l1Ratio の下限の代わりに使う値。0（純粋なリッジ回帰）は受け付けない */
  static final double MIN_L1_RATIO = 1e-12;

  private static final double TOLERANCE = 1e-10;
  private static final int MAX_ITERATIONS = 100_000;
  private static final long SEED = 0;

  private TribuoRegularization() {}

  private static List<String> featureNames(Matrix x) {
    return IntStream.range(0, x.columnCount()).mapToObj(j -> "x" + j).toList();
  }

  private static List<Features> toFeatures(Matrix x) {
    List<String> names = featureNames(x);
    return Arrays.stream(x.toArray()).map(row -> new Features(names, row)).toList();
  }

  /**
   * ElasticNetCDTrainer で学習し、係数と切片を取り出す。
   *
   * <p>Tribuo は特徴量の平均を引いてから学習するので、切片は特徴量と正解の平均値から求める。
   */
  public static RegularizedModel fitElasticNet(
      Matrix x, List<Double> t, double alpha, double l1Ratio) {
    var trainer = new ElasticNetCDTrainer(alpha, l1Ratio, TOLERANCE, MAX_ITERATIONS, false, SEED);
    var model = (SparseLinearModel) TribuoRegression.train(trainer, toFeatures(x), t);
    SparseVector weights = model.getWeights().values().iterator().next();
    List<Double> coefficients =
        featureNames(x).stream()
            .map(name -> weights.get(model.getFeatureIDMap().get(name).getID()))
            .toList();
    double[] xMeans = Ridge.columnMeans(x);
    double intercept = t.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    for (int j = 0; j < xMeans.length; j++) {
      intercept -= xMeans[j] * coefficients.get(j);
    }
    return new RegularizedModel(coefficients, intercept);
  }

  /** l1Ratio を 1 にしたラッソ回帰。 */
  public static RegularizedModel fitLasso(Matrix x, List<Double> t, double alpha) {
    return fitElasticNet(x, t, alpha, 1.0);
  }

  /**
   * 自作のリッジ回帰と同じ alpha の尺度で、ElasticNetCDTrainer にリッジ回帰を学習させる。
   *
   * <p>ElasticNetCDTrainer は誤差の 2 乗の合計を 2n で割った値に罰則を足すので、alpha を件数 n で割って渡す。
   */
  public static RegularizedModel fitRidge(Matrix x, List<Double> t, double alpha) {
    return fitElasticNet(x, t, alpha / t.size(), MIN_L1_RATIO);
  }
}
