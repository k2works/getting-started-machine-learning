package chapter09;

import chapter02.Features;
import chapter02.FeaturesAndTarget;
import chapter02.Preprocessing;
import chapter02.Row;
import chapter02.Table;
import chapter02.TrainTestSplit;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** ボストンの住宅価格（Boston.csv）の前処理と、特徴量の組ごとの決定係数。 */
public final class Boston {
  /** 正解の列 */
  public static final String TARGET = "PRICE";

  /** カテゴリ値の列 */
  public static final String CATEGORY = "CRIME";

  private Boston() {}

  /** CRIME をダミー変数にし、分割してから訓練データの平均値で両方の欠損値を補完する。 */
  public static TrainTestSplit<Features, Double> prepare(Path csvFile, double testSize, long seed)
      throws IOException {
    Table table = Table.load(csvFile);
    List<String> crimes = table.rows().stream().map(row -> row.text(CATEGORY)).toList();
    Table encoded = Dummies.encode(table, CATEGORY, Dummies.categories(crimes));
    FeaturesAndTarget data = Preprocessing.splitFeaturesAndTarget(encoded, TARGET);
    List<Double> prices = data.target().stream().map(Double::parseDouble).toList();
    TrainTestSplit<Row, Double> split =
        Preprocessing.splitTrainTest(data.rows(), prices, testSize, seed);
    Map<String, Double> means = Preprocessing.columnMeans(split.xTrain(), data.columns());
    return new TrainTestSplit<>(
        Preprocessing.fillMissing(split.xTrain(), data.columns(), means),
        Preprocessing.fillMissing(split.xTest(), data.columns(), means),
        split.tTrain(),
        split.tTest());
  }

  /** 列から多項式特徴量を作って terms の項を選び、訓練データで標準化してから線形回帰で学習し、決定係数を求める。 */
  public static Scores scoreFeatureSet(
      TrainTestSplit<Features, Double> split, List<String> columns, List<String> terms) {
    List<Features> train =
        PolynomialFeatures.select(PolynomialFeatures.expand(split.xTrain(), columns), terms);
    List<Features> test =
        PolynomialFeatures.select(PolynomialFeatures.expand(split.xTest(), columns), terms);
    Standardizer standardizer = Standardizer.fit(train);
    double[][] xTrain = toRows(standardizer.transform(train));
    double[][] xTest = toRows(standardizer.transform(test));
    LinearModel model = LinearModel.fit(xTrain, split.tTrain());
    return new Scores(
        LinearModel.rSquared(split.tTrain(), model.predict(xTrain)),
        LinearModel.rSquared(split.tTest(), model.predict(xTest)));
  }

  private static double[][] toRows(List<Features> x) {
    return x.stream().map(Features::values).toArray(double[][]::new);
  }
}
