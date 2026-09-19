package chapter10;

import static org.assertj.core.api.Assertions.assertThat;

import chapter02.Features;
import chapter02.TrainTestSplit;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EvaluateTest {
  /** どんな特徴量にも setosa と答えるモデル。 */
  private static final class AlwaysSetosa implements Classifier {
    @Override
    public AlwaysSetosa fit(List<Features> x, List<String> t) {
      return this;
    }

    @Override
    public List<String> predict(List<Features> x) {
      return Collections.nCopies(x.size(), "setosa");
    }
  }

  private static TrainTestSplit<Features, String> smallSplit() {
    return new TrainTestSplit<>(
        Samples.column("花弁幅", 0.1, 0.2, 0.8, 0.9),
        Samples.column("花弁幅", 0.15, 0.25),
        List.of("setosa", "setosa", "virginica", "virginica"),
        List.of("setosa", "setosa"));
  }

  @Test
  @DisplayName("学習させてから訓練データとテストデータの正解率を求める")
  void evaluate() {
    assertThat(Score.evaluate(new AlwaysSetosa(), smallSplit())).isEqualTo(new Score(0.5, 1.0));
  }

  @Test
  @DisplayName("第 3 章の決定木と自作のモデルを同じ関数で評価できる")
  void sameFunction() {
    List<Classifier> models =
        List.of(
            DecisionTreeClassifier.withMaxDepth(1),
            new LogisticRegression(),
            RandomForest.of(5, 1, 0));

    var scores = models.stream().map(model -> Score.evaluate(model, smallSplit())).toList();

    assertThat(scores).containsOnly(new Score(1.0, 1.0)).hasSize(3);
  }
}
