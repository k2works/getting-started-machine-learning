package chapter10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import chapter02.Features;
import chapter02.Preprocessing;
import chapter02.TrainTestSplit;
import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tribuo.Trainer;
import org.tribuo.classification.dtree.CARTClassificationTrainer;
import org.tribuo.classification.dtree.impurity.GiniIndex;
import org.tribuo.classification.ensemble.VotingCombiner;
import org.tribuo.classification.sgd.linear.LinearSGDTrainer;
import org.tribuo.classification.sgd.linear.LogisticRegressionTrainer;
import org.tribuo.classification.sgd.objectives.LogMulticlass;
import org.tribuo.common.tree.RandomForestTrainer;
import org.tribuo.math.optimisers.AdaGrad;
import support.StdoutCapture;

class IrisModelsTest {
  private final Path csvFile = DataDir.dataDir().resolve("iris.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(Files.exists(csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");
  }

  private TrainTestSplit<Features, String> irisSplit() throws IOException {
    return Preprocessing.prepareIris(csvFile, 0.3, 0);
  }

  private static TribuoClassifier tribuoLogisticRegression(int epochs) {
    return new TribuoClassifier(
        new LinearSGDTrainer(
            new LogMulticlass(), new AdaGrad(1.0, 0.1), epochs, Trainer.DEFAULT_SEED));
  }

  @Test
  @DisplayName("ロジスティック回帰はテストデータの 45 件中 41 件を正しく分類する")
  void logisticRegression() throws IOException {
    Score score = Score.evaluate(new LogisticRegression(), irisSplit());

    assertThat(score.test()).isCloseTo(41.0 / 45, within(1e-12));
  }

  @Test
  @DisplayName("ランダムフォレストは訓練データを分け切りテストデータの 45 件中 42 件を正しく分類する")
  void randomForest() throws IOException {
    Score score = Score.evaluate(RandomForest.of(100, 2, 0), irisSplit());

    assertThat(score.train()).isEqualTo(1.0);
    assertThat(score.test()).isCloseTo(42.0 / 45, within(1e-12));
  }

  @Test
  @DisplayName("Tribuo の既定のロジスティック回帰は 5 エポックでテストデータの 45 件中 40 件を正しく分類する")
  void tribuoDefaultLogisticRegression() throws IOException {
    Score score =
        Score.evaluate(new TribuoClassifier(new LogisticRegressionTrainer()), irisSplit());

    assertThat(score.train()).isCloseTo(97.0 / 105, within(1e-12));
    assertThat(score.test()).isCloseTo(40.0 / 45, within(1e-12));
  }

  @Test
  @DisplayName("Tribuo の既定のロジスティック回帰と同じ設定で 5 エポックにすると既定と同じ正解率になる")
  void tribuoDefaultIsFiveEpochs() throws IOException {
    Score defaults =
        Score.evaluate(new TribuoClassifier(new LogisticRegressionTrainer()), irisSplit());

    assertThat(Score.evaluate(tribuoLogisticRegression(5), irisSplit())).isEqualTo(defaults);
  }

  @Test
  @DisplayName("Tribuo のロジスティック回帰は 500 エポックで自作と同じ正解率になる")
  void tribuoFiveHundredEpochs() throws IOException {
    Score tribuo = Score.evaluate(tribuoLogisticRegression(500), irisSplit());

    assertThat(tribuo).isEqualTo(Score.evaluate(new LogisticRegression(), irisSplit()));
  }

  @Test
  @DisplayName("Tribuo のランダムフォレストはテストデータの 45 件中 43 件を正しく分類するが訓練データは分け切らない")
  void tribuoRandomForest() throws IOException {
    Score score =
        Score.evaluate(
            TribuoClassifier.randomForest(100, TribuoClassifier.UNLIMITED, 0L), irisSplit());

    assertThat(score.train()).isCloseTo(104.0 / 105, within(1e-12));
    assertThat(score.test()).isCloseTo(43.0 / 45, within(1e-12));
  }

  @Test
  @DisplayName("Tribuo のランダムフォレストは最小の重みを 1 にすると訓練データを分け切る")
  void tribuoRandomForestMinChildWeightOne() throws IOException {
    var tree =
        new CARTClassificationTrainer(Integer.MAX_VALUE, 1.0f, 0.0f, 0.5f, new GiniIndex(), 0L);
    var forest =
        new TribuoClassifier(new RandomForestTrainer<>(tree, new VotingCombiner(), 100, 0L));

    Score score = Score.evaluate(forest, irisSplit());

    assertThat(score.train()).isEqualTo(1.0);
    assertThat(score.test()).isCloseTo(42.0 / 45, within(1e-12));
  }

  @Test
  @DisplayName("実行するとモデルごとの正解率とランダムフォレストの重要度を表示する")
  void mainPrintsSummary() throws Exception {
    String output = StdoutCapture.capture(() -> Main.main(new String[0]));

    assertThat(output)
        .isEqualTo(
            """
            モデル\t訓練データ\tテストデータ
            決定木（深さ 2）\t0.9333\t0.9556
            ロジスティック回帰\t0.9143\t0.9111
            ランダムフォレスト（100 本）\t1.0000\t0.9333
            ランダムフォレスト（100 本・深さ 2）\t0.9429\t0.9556
            Tribuo ロジスティック回帰\t0.9238\t0.8889
            Tribuo ランダムフォレスト（100 本）\t0.9905\t0.9556

            ランダムフォレスト（100 本）の特徴量の重要度:
            がく片長さ\t0.1882
            がく片幅\t0.1271
            花弁長さ\t0.2708
            花弁幅\t0.4140
            """);
  }
}
