package chapter07;

import chapter02.Features;
import chapter02.Table;
import chapter02.TrainTestSplit;
import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** 映画の興行収入を線形回帰で予測し、係数と評価指標を表示する。 */
public final class Main {
  private static final double TEST_SIZE = 0.2;
  private static final long SEED = 0;

  private Main() {}

  public static void main(String[] args) throws IOException {
    Path csvFile = DataDir.dataDir().resolve("cinema.csv");
    Table table = Table.load(csvFile);
    TrainTestSplit<Features, Double> split = Cinema.prepare(csvFile, TEST_SIZE, SEED);
    LinearModel model = LinearRegression.fit(split.xTrain(), split.tTrain());
    List<Double> y = model.predict(split.xTest());
    String coefficients =
        model.coefficients().entrySet().stream()
            .map(entry -> entry.getKey() + "=" + fourDecimals(entry.getValue()))
            .collect(Collectors.joining(", "));
    System.out.println("データ件数: " + table.rows().size());
    System.out.println("外れ値を除いた件数: " + Cinema.removeOutliers(table).rows().size());
    System.out.println(
        "訓練データ: " + split.xTrain().size() + " 件, テストデータ: " + split.xTest().size() + " 件");
    System.out.println("切片: " + twoDecimals(model.intercept()));
    System.out.println("係数: " + coefficients);
    System.out.println(
        "テストデータの評価: R2="
            + fourDecimals(RegressionMetrics.r2Score(split.tTest(), y))
            + ", MAE="
            + twoDecimals(RegressionMetrics.meanAbsoluteError(split.tTest(), y))
            + ", RMSE="
            + twoDecimals(RegressionMetrics.rootMeanSquaredError(split.tTest(), y)));
  }

  private static String fourDecimals(double value) {
    return String.format(Locale.ROOT, "%.4f", value);
  }

  private static String twoDecimals(double value) {
    return String.format(Locale.ROOT, "%.2f", value);
  }
}
