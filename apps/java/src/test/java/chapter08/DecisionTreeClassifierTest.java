package chapter08;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import chapter02.Features;
import chapter03.DecisionTree;
import chapter03.DecisionTrees;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DecisionTreeClassifierTest {
  @Test
  @DisplayName("重みがすべて 1 なら第 3 章のジニ不純度と同じ値になる")
  void unitWeights() {
    var labels = List.of(0, 1, 1);

    assertThat(WeightedTrees.weightedGini(labels, List.of(1.0, 1.0, 1.0)))
        .isEqualTo(DecisionTrees.gini(labels.stream().map(String::valueOf).toList()));
  }

  @Test
  @DisplayName("重みの大きいラベルほど多いものとして不純度を計算する")
  void weighted() {
    assertThat(WeightedTrees.weightedGini(List.of(0, 1), List.of(1.0, 3.0)))
        .isCloseTo(0.375, within(1e-12));
  }

  @Test
  @DisplayName("少ないクラスほど 1 件の重みを大きくし、クラスごとの重みの合計をそろえる")
  void balancedWeights() {
    var weights = WeightedTrees.balancedWeights(List.of(0, 0, 0, 1));

    assertThat(weights).containsExactly(4.0 / 6, 4.0 / 6, 4.0 / 6, 2.0);
    assertThat(weights.get(0) + weights.get(1) + weights.get(2))
        .isCloseTo(weights.get(3), within(1e-12));
  }

  private static List<Features> fareAndAge(double[]... rows) {
    return Arrays.stream(rows).map(row -> new Features(List.of("Fare", "Age"), row)).toList();
  }

  @ParameterizedTest(name = "深さ {0}（-1 は制限なし）")
  @ValueSource(ints = {1, 2, DecisionTreeClassifier.UNLIMITED})
  @DisplayName("重み付けなしなら第 3 章の決定木と同じ予測をする")
  void sameAsChapter03(int maxDepth) {
    var x =
        fareAndAge(
            new double[] {8, 30},
            new double[] {9, 22},
            new double[] {13, 18},
            new double[] {20, 45},
            new double[] {60, 25},
            new double[] {80, 33});
    var t = List.of(0, 0, 1, 0, 1, 1);
    var chapter03 =
        maxDepth == DecisionTreeClassifier.UNLIMITED
            ? DecisionTree.unlimited()
            : DecisionTree.withMaxDepth(maxDepth);
    var expected = chapter03.fit(x, t.stream().map(String::valueOf).toList()).predict(x);

    var predictions = new DecisionTreeClassifier(maxDepth, ClassWeight.NONE).fit(x, t).predict(x);

    assertThat(predictions.stream().map(String::valueOf).toList()).isEqualTo(expected);
  }

  @Test
  @DisplayName("balanced にすると、少ないクラスが混ざった葉でも少ないクラスを予測する")
  void balanced() {
    var fare = column("Fare", 1, 1, 1, 1, 2, 2, 2);
    var survived = List.of(0, 0, 0, 0, 0, 0, 1);
    var newX = column("Fare", 1, 2);

    var none = new DecisionTreeClassifier(1, ClassWeight.NONE).fit(fare, survived);
    var balanced = new DecisionTreeClassifier(1, ClassWeight.BALANCED).fit(fare, survived);

    assertThat(none.predict(newX)).containsExactly(0, 0);
    assertThat(balanced.predict(newX)).containsExactly(0, 1);
  }

  private static List<Features> column(String name, double... values) {
    return Arrays.stream(values)
        .mapToObj(v -> new Features(List.of(name), new double[] {v}))
        .toList();
  }
}
