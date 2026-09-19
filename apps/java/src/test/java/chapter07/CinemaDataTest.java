package chapter07;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import chapter02.Table;
import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tribuo.regression.slm.LARSTrainer;
import org.tribuo.regression.slm.SLMTrainer;
import support.StdoutCapture;

class CinemaDataTest {
  private final Path csvFile = DataDir.dataDir().resolve("cinema.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(Files.exists(csvFile), "学習データ cinema.csv が配置されていない（gulp data:setup）");
  }

  @Test
  @DisplayName("実データから外れ値を 1 件取り除く")
  void removesOneOutlier() throws IOException {
    Table table = Table.load(csvFile);

    assertThat(List.of(table.rows().size(), Cinema.removeOutliers(table).rows().size()))
        .containsExactly(100, 99);
  }

  @Test
  @DisplayName("実データで自作のモデルと Tribuo の SLMTrainer・LARSTrainer の R² が一致する")
  void sameR2AsTribuo() throws IOException {
    var split = Cinema.prepare(csvFile, 0.2, 0);
    double mine =
        RegressionMetrics.r2Score(
            split.tTest(),
            LinearRegression.fit(split.xTrain(), split.tTrain()).predict(split.xTest()));

    for (var trainer : List.of(new SLMTrainer(true), new LARSTrainer())) {
      var model = TribuoRegression.train(trainer, split.xTrain(), split.tTrain());
      double tribuo =
          RegressionMetrics.r2Score(split.tTest(), TribuoRegression.predict(model, split.xTest()));
      assertThat(tribuo).isCloseTo(mine, within(1e-9));
    }
  }

  @Test
  @DisplayName("実行すると学習した係数と評価指標を表示する")
  void mainPrintsSummary() throws Exception {
    String output = StdoutCapture.capture(() -> Main.main(new String[0]));

    assertThat(output)
        .isEqualTo(
            """
            データ件数: 100
            外れ値を除いた件数: 99
            訓練データ: 79 件, テストデータ: 20 件
            切片: 6114.60
            係数: SNS1=1.3804, SNS2=0.5218, actor=0.2900, original=208.8827
            テストデータの評価: R2=0.6184, MAE=302.20, RMSE=376.14
            """);
  }
}
