package chapter09;

import chapter02.Features;
import chapter02.TrainTestSplit;
import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** ボストンの住宅価格で特徴量エンジニアリングを試し、自転車の利用者数に天気を結合して集計する。 */
public final class Main {
  /** 多項式特徴量を作る元の列 */
  public static final List<String> COLUMNS = List.of("RM", "LSTAT", "PTRATIO");

  /** 2 乗の項 */
  public static final List<String> SQUARES = List.of("RM^2", "LSTAT^2", "PTRATIO^2");

  private static final double TEST_SIZE = 0.3;
  private static final long SEED = 0;

  // 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす
  private static final double ZERO_TOLERANCE = 1e-9;

  private static final int SCORE_DIGITS = 4;
  private static final int MEAN_DIGITS = 2;
  private static final int COUNT_DIGITS = 1;

  private Main() {}

  /** 特徴量の組の名前と、使う項。 */
  public static Map<String, List<String>> featureSets() {
    List<String> interactions =
        PolynomialFeatures.pairsWithReplacement(COLUMNS).stream()
            .map(PolynomialFeatures.Pair::name)
            .toList();
    Map<String, List<String>> sets = new LinkedHashMap<>();
    sets.put("元の特徴量", COLUMNS);
    sets.put("2 乗の項を追加", concat(COLUMNS, SQUARES));
    sets.put("交互作用の項も追加", concat(COLUMNS, interactions));
    return sets;
  }

  private static List<String> concat(List<String> first, List<String> second) {
    return Stream.concat(first.stream(), second.stream()).toList();
  }

  public static void main(String[] args) throws IOException {
    Path dataDir = DataDir.dataDir();
    TrainTestSplit<Features, Double> split =
        Boston.prepare(dataDir.resolve("Boston.csv"), TEST_SIZE, SEED);
    System.out.println(
        "訓練データ: " + split.xTrain().size() + " 件, テストデータ: " + split.xTest().size() + " 件");
    System.out.println("特徴量の列: " + String.join(", ", split.xTrain().getFirst().columns()));

    // 標準化した訓練データの平均と標準偏差を、もう一度 fit して確かめる
    Standardizer check =
        Standardizer.fit(Standardizer.fit(split.xTrain()).transform(split.xTrain()));
    System.out.println(
        "標準化した訓練データの RM: 平均 "
            + format(check.means().get("RM"), MEAN_DIGITS)
            + ", 標準偏差 "
            + format(check.stds().get("RM"), MEAN_DIGITS));

    System.out.println("決定係数:");
    featureSets()
        .forEach(
            (name, terms) ->
                System.out.println(
                    "  "
                        + name
                        + "（"
                        + terms.size()
                        + " 列）: "
                        + formatScores(Boston.scoreFeatureSet(split, COLUMNS, terms))));

    long outliers = Outliers.iqrOutliers(split.tTrain()).stream().filter(b -> b).count();
    System.out.println("訓練データの PRICE の外れ値: " + outliers + " 件");
    Scores removed =
        Boston.scoreFeatureSet(
            Outliers.removeTargetOutliers(split), COLUMNS, concat(COLUMNS, SQUARES));
    System.out.println("  外れ値を除いて 2 乗の項を追加: " + formatScores(removed));

    var joined =
        BikeWeather.joinWeather(
            BikeWeather.loadBike(dataDir.resolve("bike.tsv")),
            BikeWeather.loadWeather(dataDir.resolve("weather.csv")));
    System.out.println(
        "天気ごとの平均利用者数: "
            + BikeWeather.meanCountByWeather(joined).entrySet().stream()
                .map(e -> e.getKey() + "=" + format(e.getValue(), COUNT_DIGITS))
                .collect(Collectors.joining(", ")));
  }

  private static String format(double value, int digits) {
    return String.format(
        Locale.ROOT, "%." + digits + "f", Math.abs(value) < ZERO_TOLERANCE ? 0.0 : value);
  }

  private static String formatScores(Scores scores) {
    return "訓練 "
        + format(scores.train(), SCORE_DIGITS)
        + ", テスト "
        + format(scores.test(), SCORE_DIGITS);
  }
}
