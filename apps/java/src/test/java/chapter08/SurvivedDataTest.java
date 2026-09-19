package chapter08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import chapter02.Features;
import chapter02.Preprocessing;
import chapter02.Row;
import chapter02.Table;
import chapter02.TrainTestSplit;
import chapter03.DecisionTree;
import chapter03.TribuoTrees;
import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import support.StdoutCapture;

class SurvivedDataTest {
  private final Path csvFile = DataDir.dataDir().resolve("Survived.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(Files.exists(csvFile), "学習データ Survived.csv が配置されていない（gulp data:setup）");
  }

  private TrainTestSplit<Row, Integer> survivedSplit() throws IOException {
    List<Row> rows = Table.load(csvFile).rows();
    return Preprocessing.splitTrainTest(rows, SurvivedData.target(rows), 0.2, 0);
  }

  private static FittedPipeline fit(
      TrainTestSplit<Row, Integer> split, int maxDepth, ClassWeight w) {
    return Pipeline.build(maxDepth, w).fit(SurvivedData.features(split.xTrain()), split.tTrain());
  }

  private static Evaluation evaluate(
      TrainTestSplit<Row, Integer> split, int maxDepth, ClassWeight classWeight) {
    return Evaluation.evaluate(fit(split, maxDepth, classWeight), split);
  }

  /** 2 つの予測のうち、違うものの件数。 */
  private static long mismatches(List<String> a, List<String> b) {
    return IntStream.range(0, a.size()).filter(i -> !a.get(i).equals(b.get(i))).count();
  }

  private static List<String> strings(List<Integer> labels) {
    return labels.stream().map(String::valueOf).toList();
  }

  @Test
  @DisplayName("実データの件数と欠損値の数を確認する")
  void countsAndMissing() throws IOException {
    Table table = Table.load(csvFile);

    assertThat(table.rows()).hasSize(891);
    assertThat(table.countMissing())
        .containsEntry("Age", 177)
        .containsEntry("Cabin", 687)
        .containsEntry("Embarked", 2);
  }

  @Test
  @DisplayName("深さ 2 では balanced にすると見つけられる生存者が 41 人から 68 人に増える")
  void depthTwo() throws IOException {
    var split = survivedSplit();

    assertThat(evaluate(split, 2, ClassWeight.NONE).foundSurvivors()).isEqualTo(41);
    assertThat(evaluate(split, 2, ClassWeight.BALANCED).foundSurvivors()).isEqualTo(68);
  }

  @Test
  @DisplayName("深さ 5 では balanced にすると見つけられる生存者が 59 人から 65 人に増える")
  void depthFive() throws IOException {
    var split = survivedSplit();

    var none = evaluate(split, 5, ClassWeight.NONE);
    var balanced = evaluate(split, 5, ClassWeight.BALANCED);

    assertThat(none.foundSurvivors()).isEqualTo(59);
    assertThat(balanced.foundSurvivors()).isEqualTo(65);
    assertThat(balanced.testAccuracy()).isCloseTo(0.804, within(1e-3));
  }

  /** 深さと、Tribuo の CART と予測が違う件数（実測した値）。 */
  static Stream<Arguments> mismatchesWithTribuo() {
    return Stream.of(
        arguments(1, 0L),
        arguments(2, 0L),
        arguments(3, 0L),
        arguments(4, 0L),
        arguments(5, 2L),
        arguments(6, 0L),
        arguments(7, 0L),
        arguments(8, 0L),
        arguments(9, 1L),
        arguments(10, 0L));
  }

  @ParameterizedTest(name = "深さ {0} で {1} 件")
  @MethodSource("mismatchesWithTribuo")
  @DisplayName("前処理後のテストデータ 179 件で Tribuo の CART と予測が違う件数")
  void comparedWithTribuo(int maxDepth, long expected) throws IOException {
    var split = survivedSplit();
    var pipeline = fit(split, maxDepth, ClassWeight.NONE);
    List<Features> xTrain = pipeline.features(SurvivedData.features(split.xTrain()));
    List<Features> xTest = pipeline.features(SurvivedData.features(split.xTest()));

    var tribuo =
        TribuoTrees.predict(TribuoTrees.train(xTrain, strings(split.tTrain()), maxDepth), xTest);

    assertThat(mismatches(strings(pipeline.model().predict(xTest)), tribuo)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "深さ {0}")
  @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
  @DisplayName("重み付けなしなら、前処理後のテストデータで第 3 章の決定木と予測が一致する")
  void sameAsChapter03(int maxDepth) throws IOException {
    var split = survivedSplit();
    var pipeline = fit(split, maxDepth, ClassWeight.NONE);
    List<Features> xTrain = pipeline.features(SurvivedData.features(split.xTrain()));
    List<Features> xTest = pipeline.features(SurvivedData.features(split.xTest()));

    var chapter03 =
        DecisionTree.withMaxDepth(maxDepth).fit(xTrain, strings(split.tTrain())).predict(xTest);

    assertThat(strings(pipeline.model().predict(xTest))).isEqualTo(chapter03);
  }

  @Test
  @DisplayName("実行すると評価結果を表示してモデルを保存する")
  void mainPrintsSummary(@TempDir Path directory) throws Exception {
    Path modelFile = directory.resolve("survived.ser");

    String output = StdoutCapture.capture(() -> Main.main(modelFile));

    assertThat(modelFile).exists();
    assertThat(output)
        .isEqualTo(
            """
            データ件数: 891（生存 342, 死亡 549）
            訓練データ: 712 件, テストデータ: 179 件
            classWeight=NONE: 訓練 0.854, テスト 0.799, 生存者 79 人中 59 人を発見
            classWeight=BALANCED: 訓練 0.848, テスト 0.804, 生存者 79 人中 65 人を発見
            保存したモデル: survived.ser
            架空の乗客の予測: [1, 0]
            """);
  }
}
