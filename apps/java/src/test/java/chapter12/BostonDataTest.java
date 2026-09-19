package chapter12;

import static chapter12.Samples.assertDoubles;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import support.StdoutCapture;

class BostonDataTest {
  private final Path csvFile = DataDir.dataDir().resolve("Boston.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(Files.exists(csvFile), "学習データ Boston.csv が配置されていない（gulp data:setup）");
  }

  private Boston.Dataset dataset() throws IOException {
    return Boston.prepare(csvFile, 0.3, 0.3, 0);
  }

  @Test
  @DisplayName("外れ値を除いて訓練データと検証データとテストデータに分ける")
  void sizes() throws IOException {
    var data = dataset();

    assertThat(List.of(data.tTrain().size(), data.tValid().size(), data.tTest().size()))
        .containsExactly(47, 21, 30);
  }

  @Test
  @DisplayName("実データでも自作のリッジ回帰は Tribuo の ElasticNetCDTrainer と同じ係数と切片になる")
  void sameAsTribuo() throws IOException {
    var data = dataset();

    var model = Ridge.fit(data.xTrain(), data.tTrain(), 10.0);
    var tribuo = TribuoRegularization.fitRidge(data.xTrain(), data.tTrain(), 10.0);

    assertDoubles(model.coefficients(), tribuo.coefficients(), 1e-6);
    assertThat(tribuo.intercept()).isCloseTo(model.intercept(), within(1e-6));
  }

  @Test
  @DisplayName("実行すると正則化の実験結果を表示する")
  void mainPrintsExperiments() throws Exception {
    String output = StdoutCapture.capture(() -> Main.main(new String[0]));

    assertThat(output)
        .isEqualTo(
            """
            データ件数: 98（外れ値 2 件を除外）
            訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件
            特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2
            alpha  訓練 R²  検証 R²  係数の絶対値の合計
              0.0  0.8827  0.7272  14.187
              0.1  0.8827  0.7274  14.104
              1.0  0.8823  0.7288  13.594
             10.0  0.8681  0.7349  11.573
            100.0  0.6583  0.5985  5.684
            検証データで選んだ alpha: 10.0
            テストデータの決定係数: 線形回帰 0.5224, リッジ回帰 0.6243
            ラッソ回帰（alpha=0.5）で係数が 0 になった特徴量: PTRATIO^2, PTRATIO LSTAT, LSTAT^2
            """);
  }
}
