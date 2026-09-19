package chapter09;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import chapter02.Features;
import chapter02.Table;
import chapter02.TrainTestSplit;
import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import support.StdoutCapture;

class FeatureEngineeringDataTest {
  private final Path bostonCsv = DataDir.dataDir().resolve("Boston.csv");
  private final Path bikeTsv = DataDir.dataDir().resolve("bike.tsv");
  private final Path weatherCsv = DataDir.dataDir().resolve("weather.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(
        Stream.of(bostonCsv, bikeTsv, weatherCsv).allMatch(Files::exists),
        "学習データ Boston.csv・bike.tsv・weather.csv が配置されていない（gulp data:setup）");
  }

  private TrainTestSplit<Features, Double> bostonSplit() throws IOException {
    return Boston.prepare(bostonCsv, 0.3, 0);
  }

  @Test
  @DisplayName("実データを 70 件と 30 件に分けて欠損値を補完する")
  void splitsBoston() throws IOException {
    var split = bostonSplit();

    assertThat(split.xTrain()).hasSize(70);
    assertThat(split.xTest()).hasSize(30);
  }

  @Test
  @DisplayName("2 乗の項を加えるとテストデータの決定係数が上がる")
  void squaresImproveTestScore() throws IOException {
    var split = bostonSplit();

    Scores base = Boston.scoreFeatureSet(split, Main.COLUMNS, Main.COLUMNS);
    Scores squares =
        Boston.scoreFeatureSet(
            split,
            Main.COLUMNS,
            Stream.concat(Main.COLUMNS.stream(), Main.SQUARES.stream()).toList());

    assertThat(base.test()).isCloseTo(0.6950, within(1e-4));
    assertThat(squares.test()).isCloseTo(0.8628, within(1e-4));
  }

  @Test
  @DisplayName("weather.csv は Shift_JIS で、bike.tsv と結合すると 731 行になる")
  void joinsBikeAndWeather() throws IOException {
    Table joined =
        BikeWeather.joinWeather(BikeWeather.loadBike(bikeTsv), BikeWeather.loadWeather(weatherCsv));

    assertThat(joined.rows()).hasSize(731);
    assertThat(BikeWeather.meanCountByWeather(joined).keySet()).containsExactly("晴れ", "曇り", "雨");
  }

  @Test
  @DisplayName("実行すると特徴量エンジニアリングの結果を表示する")
  void mainPrintsSummary() throws Exception {
    String output = StdoutCapture.capture(() -> Main.main(new String[0]));

    assertThat(output)
        .isEqualTo(
            """
            訓練データ: 70 件, テストデータ: 30 件
            特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low
            標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00
            決定係数:
              元の特徴量（3 列）: 訓練 0.6056, テスト 0.6950
              2 乗の項を追加（6 列）: 訓練 0.7740, テスト 0.8628
              交互作用の項も追加（9 列）: 訓練 0.7953, テスト 0.8213
            訓練データの PRICE の外れ値: 8 件
              外れ値を除いて 2 乗の項を追加: 訓練 0.6717, テスト 0.7947
            天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3
            """);
  }
}
