package chapter10;

import chapter02.Features;
import chapter02.Preprocessing;
import chapter02.TrainTestSplit;
import dataset.DataDir;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.tribuo.classification.sgd.linear.LogisticRegressionTrainer;

/** モデルごとの正解率と、ランダムフォレストの特徴量の重要度を表示する。 */
public final class Main {
  private static final double TEST_SIZE = 0.3;
  private static final long SEED = 0;
  private static final int N_ESTIMATORS = 100;
  private static final int MAX_FEATURES = 2;
  private static final int SHALLOW_DEPTH = 2;

  private Main() {}

  /** 名前とモデル。表示する順に並べる。 */
  public static Map<String, Classifier> models() {
    Map<String, Classifier> models = new LinkedHashMap<>();
    models.put("決定木（深さ " + SHALLOW_DEPTH + "）", DecisionTreeClassifier.withMaxDepth(SHALLOW_DEPTH));
    models.put("ロジスティック回帰", new LogisticRegression());
    models.put(
        "ランダムフォレスト（" + N_ESTIMATORS + " 本）", RandomForest.of(N_ESTIMATORS, MAX_FEATURES, SEED));
    models.put(
        "ランダムフォレスト（" + N_ESTIMATORS + " 本・深さ " + SHALLOW_DEPTH + "）",
        RandomForest.withMaxDepth(N_ESTIMATORS, MAX_FEATURES, SHALLOW_DEPTH, SEED));
    models.put("Tribuo ロジスティック回帰", new TribuoClassifier(new LogisticRegressionTrainer()));
    models.put(
        "Tribuo ランダムフォレスト（" + N_ESTIMATORS + " 本）",
        TribuoClassifier.randomForest(N_ESTIMATORS, TribuoClassifier.UNLIMITED, SEED));
    return models;
  }

  public static void main(String[] args) throws IOException {
    // Tribuo が学習の経過を標準エラーに出すので、警告以上だけにする
    Logger.getLogger("org.tribuo").setLevel(Level.WARNING);
    TrainTestSplit<Features, String> split =
        Preprocessing.prepareIris(DataDir.dataDir().resolve("iris.csv"), TEST_SIZE, SEED);
    System.out.println("モデル\t訓練データ\tテストデータ");
    models()
        .forEach(
            (name, model) -> {
              Score score = Score.evaluate(model, split);
              System.out.println(name + "\t" + format(score.train()) + "\t" + format(score.test()));
            });

    RandomForest forest =
        RandomForest.of(N_ESTIMATORS, MAX_FEATURES, SEED).fit(split.xTrain(), split.tTrain());
    System.out.println();
    System.out.println("ランダムフォレスト（" + N_ESTIMATORS + " 本）の特徴量の重要度:");
    FeatureImportance.forestImportances(forest, split.xTrain(), split.tTrain())
        .forEach((feature, value) -> System.out.println(feature + "\t" + format(value)));
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.4f", value);
  }
}
