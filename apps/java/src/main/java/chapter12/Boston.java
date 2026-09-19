package chapter12;

import chapter02.Features;
import chapter02.Preprocessing;
import chapter02.Row;
import chapter02.Table;
import chapter02.TrainTestSplit;
import chapter07.Matrix;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** ボストンの住宅価格（Boston.csv）を、外れ値を除いて訓練・検証・テストの 3 つに分ける。 */
public final class Boston {
  /** 特徴量の列 */
  public static final List<String> FEATURES = List.of("RM", "PTRATIO", "LSTAT");

  /** 正解の列 */
  public static final String TARGET = "PRICE";

  /** z スコアの絶対値がこの値を超える行を外れ値とする */
  public static final double OUTLIER_THRESHOLD = 3.0;

  private Boston() {}

  /**
   * 訓練・検証・テストの特徴量（多項式特徴量にした行列）と正解。
   *
   * @param xTrain 訓練データの特徴量
   * @param tTrain 訓練データの正解
   * @param xValid 検証データの特徴量
   * @param tValid 検証データの正解
   * @param xTest テストデータの特徴量
   * @param tTest テストデータの正解
   * @param featureNames 特徴量の列名
   */
  public record Dataset(
      Matrix xTrain,
      List<Double> tTrain,
      Matrix xValid,
      List<Double> tValid,
      Matrix xTest,
      List<Double> tTest,
      List<String> featureNames) {
    public Dataset {
      tTrain = List.copyOf(tTrain);
      tValid = List.copyOf(tValid);
      tTest = List.copyOf(tTest);
      featureNames = List.copyOf(featureNames);
    }
  }

  private static double number(Row row, String column) {
    return row.number(column).orElseThrow(() -> new IllegalArgumentException("欠損値です: " + column));
  }

  /** 列ごとの z スコア（標準偏差は件数 n - 1 で割る標本標準偏差）の絶対値が threshold を超える値を 1 つでも持つ行を除く。 */
  public static Table removeOutliers(Table table, List<String> columns, double threshold) {
    List<Row> rows = table.rows();
    double[] means = new double[columns.size()];
    double[] stds = new double[columns.size()];
    for (int j = 0; j < columns.size(); j++) {
      String column = columns.get(j);
      double[] values = rows.stream().mapToDouble(row -> number(row, column)).toArray();
      double mean = Arrays.stream(values).average().orElseThrow();
      means[j] = mean;
      stds[j] =
          Math.sqrt(
              Arrays.stream(values).map(v -> (v - mean) * (v - mean)).sum() / (values.length - 1));
    }
    List<Row> kept = new ArrayList<>();
    for (Row row : rows) {
      boolean outlier = false;
      for (int j = 0; j < columns.size(); j++) {
        if (Math.abs((number(row, columns.get(j)) - means[j]) / stds[j]) > threshold) {
          outlier = true;
        }
      }
      if (!outlier) {
        kept.add(row);
      }
    }
    return new Table(table.columns(), kept);
  }

  /**
   * 外れ値を除き、全体を訓練用とテスト用に、訓練用をさらに訓練データと検証データに分ける。
   *
   * <p>標準化の平均と標準偏差は訓練データだけから求め、3 つとも同じ値で変換する。
   */
  public static Dataset prepare(Path csvFile, double testSize, double validationSize, long seed)
      throws IOException {
    List<String> columns = new ArrayList<>(FEATURES);
    columns.add(TARGET);
    Table table = removeOutliers(Table.load(csvFile), columns, OUTLIER_THRESHOLD);
    List<Features> x =
        table.rows().stream()
            .map(
                row ->
                    new Features(
                        FEATURES,
                        FEATURES.stream().mapToDouble(column -> number(row, column)).toArray()))
            .toList();
    List<Double> t = table.rows().stream().map(row -> number(row, TARGET)).toList();
    TrainTestSplit<Features, Double> outer = Preprocessing.splitTrainTest(x, t, testSize, seed);
    TrainTestSplit<Features, Double> inner =
        Preprocessing.splitTrainTest(outer.xTrain(), outer.tTrain(), validationSize, seed);
    PolynomialScaler scaler = PolynomialScaler.fit(inner.xTrain());
    return new Dataset(
        scaler.transform(inner.xTrain()),
        inner.tTrain(),
        scaler.transform(inner.xTest()),
        inner.tTest(),
        scaler.transform(outer.xTest()),
        outer.tTest(),
        scaler.featureNames());
  }
}
