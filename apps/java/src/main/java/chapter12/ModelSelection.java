package chapter12;

import chapter07.Matrix;
import chapter07.RegressionMetrics;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/** 正則化の強さごとに実験し、検証データでモデルを選ぶ。 */
public final class ModelSelection {
  private ModelSelection() {}

  /** alpha ごとにリッジ回帰を訓練データで学習し、訓練データと検証データの決定係数を記録する。 */
  public static List<Experiment> runRidgeExperiments(
      Matrix xTrain, List<Double> tTrain, Matrix xValid, List<Double> tValid, List<Double> alphas) {
    return alphas.stream()
        .map(
            alpha -> {
              RegularizedModel model = Ridge.fit(xTrain, tTrain, alpha);
              return new Experiment(
                  alpha,
                  RegressionMetrics.r2Score(tTrain, model.predict(xTrain)),
                  RegressionMetrics.r2Score(tValid, model.predict(xValid)),
                  model.coefficientAbsSum());
            })
        .toList();
  }

  /** 検証データの決定係数が最も高い実験。同じ値なら先の実験を選ぶ。 */
  public static Experiment bestExperiment(List<Experiment> experiments) {
    return experiments.stream()
        .max(Comparator.comparingDouble(Experiment::validationScore))
        .orElseThrow(() -> new IllegalArgumentException("実験結果が 1 件もありません"));
  }

  /** 係数がちょうど 0 になった特徴量の名前を、列の順に返す。 */
  public static List<String> zeroCoefficientNames(
      List<Double> coefficients, List<String> featureNames) {
    if (coefficients.size() != featureNames.size()) {
      throw new IllegalArgumentException("係数と特徴量名の数が違います");
    }
    return IntStream.range(0, coefficients.size())
        .filter(i -> coefficients.get(i) == 0.0)
        .mapToObj(featureNames::get)
        .toList();
  }
}
