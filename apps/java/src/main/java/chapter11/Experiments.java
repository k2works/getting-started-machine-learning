package chapter11;

import chapter02.Table;
import chapter07.RegressionMetrics;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Survived.csv と cinema.csv を K 分割交差検証で評価する。 */
public final class Experiments {
  /** 分割の数 */
  public static final int N_SPLITS = 5;

  /** 分割の乱数のシード */
  public static final long SEED = 0;

  private static final int TREE_DEPTH = 2;
  private static final String SURVIVED = "1";

  /** Survived の評価指標。表示する順に並べる。 */
  public static final Map<String, Metric<String>> SURVIVED_METRICS =
      ordered(
          List.of("正解率", "適合率", "再現率", "F値"),
          List.of(
              Metrics::accuracy,
              Metrics.classificationMetric(Metrics::precision, SURVIVED),
              Metrics.classificationMetric(Metrics::recall, SURVIVED),
              Metrics.classificationMetric(Metrics::f1Score, SURVIVED)));

  /** cinema の評価指標。第 7 章の RMSE・MAE をメソッド参照で渡す。 */
  public static final Map<String, Metric<Double>> CINEMA_METRICS =
      ordered(
          List.of("RMSE", "MAE"),
          List.of(RegressionMetrics::rootMeanSquaredError, RegressionMetrics::meanAbsoluteError));

  private Experiments() {}

  private static <V> Map<String, V> ordered(List<String> names, List<V> values) {
    Map<String, V> map = new LinkedHashMap<>();
    for (int i = 0; i < names.size(); i++) {
      map.put(names.get(i), values.get(i));
    }
    return Collections.unmodifiableMap(map);
  }

  /** 同じ分割で、評価指標ごとに交差検証のスコアの平均を求める。 */
  public static <T> Map<String, Double> evaluate(
      Supplier<? extends Model<T>> makeModel, Dataset<T> data, Map<String, Metric<T>> metrics) {
    List<Fold> folds = CrossValidation.kFold(data.x().size(), N_SPLITS, SEED);
    Map<String, Double> scores = new LinkedHashMap<>();
    metrics.forEach(
        (name, metric) ->
            scores.put(
                name,
                CrossValidation.crossValidate(makeModel, data.x(), data.t(), folds, metric)
                    .average()
                    .orElseThrow()));
    return Collections.unmodifiableMap(scores);
  }

  /** Survived.csv を深さ 2 の決定木で評価する。 */
  public static Map<String, Double> evaluateSurvived(Path csvFile) throws IOException {
    return evaluate(
        () -> new DecisionTreeModel(TREE_DEPTH),
        Dataset.prepareSurvived(Table.load(csvFile)),
        SURVIVED_METRICS);
  }

  /** cinema.csv を線形回帰で評価する。 */
  public static Map<String, Double> evaluateCinema(Path csvFile) throws IOException {
    return evaluate(
        LinearRegressionModel::new, Dataset.prepareCinema(Table.load(csvFile)), CINEMA_METRICS);
  }
}
