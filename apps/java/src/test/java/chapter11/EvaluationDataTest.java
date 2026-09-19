package chapter11;

import static chapter11.TribuoEvaluationTest.pick;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import chapter02.Table;
import chapter03.TribuoTrees;
import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tribuo.classification.Label;
import org.tribuo.classification.evaluation.LabelEvaluation;
import org.tribuo.classification.evaluation.LabelEvaluator;
import support.StdoutCapture;

class EvaluationDataTest {
  private final Path survived = DataDir.dataDir().resolve("Survived.csv");
  private final Path cinema = DataDir.dataDir().resolve("cinema.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(
        Files.exists(survived) && Files.exists(cinema),
        "学習データ Survived.csv・cinema.csv が配置されていない（gulp data:setup）");
  }

  private static void assertScores(
      Map<String, Double> expected, Map<String, Double> actual, double tolerance) {
    assertThat(actual.keySet()).containsExactlyInAnyOrderElementsOf(expected.keySet());
    expected.forEach(
        (name, value) -> assertThat(actual.get(name)).as(name).isCloseTo(value, within(tolerance)));
  }

  @Test
  @DisplayName("同じ分割なら Tribuo の評価器で採点した Survived の平均と一致する")
  void survivedSameAsTribuo() throws IOException {
    var data = Dataset.prepareSurvived(Table.load(survived));
    var folds = CrossValidation.kFold(data.x().size(), Experiments.N_SPLITS, Experiments.SEED);
    var positive = new Label("1");

    List<LabelEvaluation> evaluations =
        folds.stream()
            .map(
                fold -> {
                  var model =
                      TribuoTrees.train(
                          pick(data.x(), fold.train()), pick(data.t(), fold.train()), 2);
                  var test =
                      TribuoTrees.toDataset(
                          pick(data.x(), fold.test()), pick(data.t(), fold.test()));
                  return new LabelEvaluator().evaluate(model, test);
                })
            .toList();

    var scores =
        Experiments.evaluate(
            () -> new TribuoEvaluationTest.TribuoTree(2), data, Experiments.SURVIVED_METRICS);
    assertScores(
        Map.of(
            "正解率",
                evaluations.stream().mapToDouble(LabelEvaluation::accuracy).average().orElseThrow(),
            "適合率",
                evaluations.stream()
                    .mapToDouble(e -> e.precision(positive))
                    .average()
                    .orElseThrow(),
            "再現率",
                evaluations.stream().mapToDouble(e -> e.recall(positive)).average().orElseThrow(),
            "F値", evaluations.stream().mapToDouble(e -> e.f1(positive)).average().orElseThrow()),
        scores,
        1e-12);
  }

  @Test
  @DisplayName("実行すると交差検証の平均を表示する")
  void mainPrintsScores() throws Exception {
    String output = StdoutCapture.capture(() -> Main.main(new String[0]));

    assertThat(output)
        .isEqualTo(
            """
            Survived（決定木、5 分割交差検証の平均）
              正解率: 0.7811
              適合率: 0.7759
              再現率: 0.6306
              F値: 0.6833
            cinema（線形回帰、5 分割交差検証の平均）
              RMSE: 405.77
              MAE: 321.53
            """);
  }
}
