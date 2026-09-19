package chapter12;

import chapter02.Table;
import chapter07.RegressionMetrics;
import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.tribuo.regression.slm.ElasticNetCDTrainer;

/** 正則化の強さごとにリッジ回帰を学習し、検証データで選んだモデルとラッソ回帰の結果を表示する。 */
public final class Main {
  private static final double TEST_SIZE = 0.3;
  private static final double VALIDATION_SIZE = 0.3;
  private static final long SEED = 0;
  private static final List<Double> ALPHAS = List.of(0.0, 0.1, 1.0, 10.0, 100.0);
  private static final double LASSO_ALPHA = 0.5;

  // Tribuo の座標降下法が収束のたびに出す INFO のログを表示しない。
  // Logger は弱い参照で管理されるので、設定した Logger を static で持ち続ける
  private static final Logger TRIBUO_LOGGER = Logger.getLogger(ElasticNetCDTrainer.class.getName());

  private Main() {}

  public static void main(String[] args) throws IOException {
    TRIBUO_LOGGER.setLevel(Level.WARNING);
    Path csvFile = DataDir.dataDir().resolve("Boston.csv");
    Table table = Table.load(csvFile);
    List<String> columns = new ArrayList<>(Boston.FEATURES);
    columns.add(Boston.TARGET);
    int kept = Boston.removeOutliers(table, columns, Boston.OUTLIER_THRESHOLD).rows().size();
    Boston.Dataset data = Boston.prepare(csvFile, TEST_SIZE, VALIDATION_SIZE, SEED);
    System.out.println("データ件数: " + kept + "（外れ値 " + (table.rows().size() - kept) + " 件を除外）");
    System.out.println(
        "訓練データ: "
            + data.tTrain().size()
            + " 件, 検証データ: "
            + data.tValid().size()
            + " 件, テストデータ: "
            + data.tTest().size()
            + " 件");
    System.out.println("特徴量: " + String.join(", ", data.featureNames()));

    List<Experiment> experiments =
        ModelSelection.runRidgeExperiments(
            data.xTrain(), data.tTrain(), data.xValid(), data.tValid(), ALPHAS);
    System.out.println("alpha  訓練 R²  検証 R²  係数の絶対値の合計");
    for (Experiment e : experiments) {
      System.out.println(
          String.format(
              Locale.ROOT,
              "%5.1f  %.4f  %.4f  %.3f",
              e.alpha(),
              e.trainScore(),
              e.validationScore(),
              e.coefficientAbsSum()));
    }
    Experiment best = ModelSelection.bestExperiment(experiments);
    System.out.println("検証データで選んだ alpha: " + best.alpha());

    RegularizedModel linear = Ridge.fit(data.xTrain(), data.tTrain(), 0.0);
    RegularizedModel ridge = Ridge.fit(data.xTrain(), data.tTrain(), best.alpha());
    System.out.println(
        String.format(
            Locale.ROOT,
            "テストデータの決定係数: 線形回帰 %.4f, リッジ回帰 %.4f",
            RegressionMetrics.r2Score(data.tTest(), linear.predict(data.xTest())),
            RegressionMetrics.r2Score(data.tTest(), ridge.predict(data.xTest()))));

    RegularizedModel lasso =
        TribuoRegularization.fitLasso(data.xTrain(), data.tTrain(), LASSO_ALPHA);
    System.out.println(
        "ラッソ回帰（alpha="
            + LASSO_ALPHA
            + "）で係数が 0 になった特徴量: "
            + String.join(
                ", ",
                ModelSelection.zeroCoefficientNames(lasso.coefficients(), data.featureNames())));
  }
}
