package chapter07;

import chapter02.Features;
import chapter02.Preprocessing;
import chapter02.Row;
import chapter02.Table;
import chapter02.TrainTestSplit;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 映画の興行収入のデータ（cinema.csv）の前処理。 */
public final class Cinema {
  /** 特徴量の列 */
  public static final List<String> FEATURES = List.of("SNS1", "SNS2", "actor", "original");

  /** 正解ラベルの列 */
  public static final String TARGET = "sales";

  /** SNS2 がこの値を超え、かつ興行収入が OUTLIER_SALES 未満の映画を外れ値とする */
  private static final double OUTLIER_SNS2 = 1000;

  private static final double OUTLIER_SALES = 8500;

  private Cinema() {}

  private static double number(Row row, String column) {
    return row.number(column).orElseThrow(() -> new IllegalArgumentException("欠損値です: " + column));
  }

  private static boolean isOutlier(Row row) {
    return number(row, "SNS2") > OUTLIER_SNS2 && number(row, TARGET) < OUTLIER_SALES;
  }

  /** 外れ値の行を除いた表を返す。 */
  public static Table removeOutliers(Table table) {
    return new Table(
        table.columns(), table.rows().stream().filter(row -> !isOutlier(row)).toList());
  }

  /** 外れ値を除き、分割してから訓練データの平均値で両方の欠損値を補完する。 */
  public static TrainTestSplit<Features, Double> prepare(Path csvFile, double testSize, long seed)
      throws IOException {
    Table table = removeOutliers(Table.load(csvFile));
    List<Double> t = table.rows().stream().map(row -> number(row, TARGET)).toList();
    TrainTestSplit<Row, Double> split =
        Preprocessing.splitTrainTest(table.rows(), t, testSize, seed);
    Map<String, Double> means = Preprocessing.columnMeans(split.xTrain(), FEATURES);
    return new TrainTestSplit<>(
        Preprocessing.fillMissing(split.xTrain(), FEATURES, means),
        Preprocessing.fillMissing(split.xTest(), FEATURES, means),
        split.tTrain(),
        split.tTest());
  }
}
