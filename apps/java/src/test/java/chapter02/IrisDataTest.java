package chapter02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import support.StdoutCapture;

class IrisDataTest {
  private final Path csvFile = DataDir.dataDir().resolve("iris.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(Files.exists(csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");
  }

  @Test
  @DisplayName("実データの列ごとの欠損値の数を数える")
  void countsMissing() throws IOException {
    assertThat(Table.load(csvFile).countMissing())
        .containsExactly(
            Map.entry("がく片長さ", 2),
            Map.entry("がく片幅", 1),
            Map.entry("花弁長さ", 2),
            Map.entry("花弁幅", 2),
            Map.entry("種類", 0));
  }

  @Test
  @DisplayName("実データを 105 件と 45 件に分けて欠損値を補完する")
  void splitsAndFills() throws IOException {
    TrainTestSplit<Features, String> split = Preprocessing.prepareIris(csvFile, 0.3, 0);

    assertThat(split.xTrain()).hasSize(105);
    assertThat(split.xTest()).hasSize(45);
    assertThat(split.tTrain()).hasSize(105);
    assertThat(split.tTest()).hasSize(45);
  }

  @Test
  @DisplayName("実行すると前処理の結果を表示する")
  void mainPrintsSummary() throws Exception {
    String output = StdoutCapture.capture(() -> Main.main(new String[0]));

    assertThat(output)
        .isEqualTo(
            """
            データ件数: 150
            欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0
            訓練データ: 105 件, テストデータ: 45 件
            特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅
            """);
  }
}
