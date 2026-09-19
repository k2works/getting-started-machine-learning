package chapter10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chapter02.Features;
import chapter02.TrainTestSplit;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tribuo.classification.dtree.CARTClassificationTrainer;
import org.tribuo.classification.ensemble.VotingCombiner;
import org.tribuo.classification.sgd.linear.LogisticRegressionTrainer;
import org.tribuo.common.tree.RandomForestTrainer;

class TribuoClassifierTest {
  private static TrainTestSplit<Features, String> twoSpeciesSplit() {
    return new TrainTestSplit<>(
        Samples.twoSpeciesX(),
        Samples.twoSpeciesNewX(),
        Samples.twoSpeciesT(),
        List.of("setosa", "virginica"));
  }

  @Test
  @DisplayName("Tribuo のロジスティック回帰とランダムフォレストも同じ関数で評価できる")
  void sameFunction() {
    List<Classifier> models =
        List.of(
            new TribuoClassifier(new LogisticRegressionTrainer()),
            TribuoClassifier.randomForest(10, TribuoClassifier.UNLIMITED, 0L));

    var scores = models.stream().map(model -> Score.evaluate(model, twoSpeciesSplit())).toList();

    assertThat(scores).containsOnly(new Score(1.0, 1.0)).hasSize(2);
  }

  @Test
  @DisplayName("学習する前に予測するとエラーになる")
  void predictBeforeFit() {
    assertThatThrownBy(
            () ->
                new TribuoClassifier(new LogisticRegressionTrainer())
                    .predict(Samples.column("花弁幅", 0.1)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("fit で学習してから predict を呼んでください");
  }

  @Test
  @DisplayName("Tribuo のランダムフォレストは分割ごとに特徴量を絞らない決定木を受け付けない")
  void tribuoRandomForestRequiresFraction() {
    var tree = new CARTClassificationTrainer(Integer.MAX_VALUE);

    assertThatThrownBy(() -> new RandomForestTrainer<>(tree, new VotingCombiner(), 100, 0L))
        .hasMessageContaining("requires that the decision tree innerTrainer have fractional");
  }
}
