package chapter10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.within;

import chapter03.DecisionTree;
import chapter03.Leaf;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class FeatureImportanceTest {
  @Nested
  class TreeImportances {
    @Test
    @DisplayName("分割しない木はすべての特徴量の重要度が 0")
    void leaf() {
      var x = Samples.columns("がく片幅", new double[] {0.3, 0.5}, "花弁幅", new double[] {0.1, 0.2});

      assertThat(
              FeatureImportance.treeImportances(new Leaf("setosa"), x, List.of("setosa", "setosa")))
          .containsExactly(entry("がく片幅", 0.0), entry("花弁幅", 0.0));
    }

    @Test
    @DisplayName("1 回だけ分割する木は分割に使った特徴量の重要度が 1")
    void singleSplit() {
      var x =
          Samples.columns(
              "がく片幅", new double[] {0.3, 0.5, 0.4, 0.6}, "花弁幅", new double[] {0.1, 0.2, 0.8, 0.9});
      var t = List.of("setosa", "setosa", "virginica", "virginica");
      var tree = DecisionTree.unlimited().fit(x, t).tree().orElseThrow();

      var importances = FeatureImportance.treeImportances(tree, x, t);

      assertThat(importances).containsKeys("がく片幅", "花弁幅");
      assertThat(importances.get("がく片幅")).isEqualTo(0.0);
      assertThat(importances.get("花弁幅")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("分割で減った不純度を件数で重み付けして割合にする")
    void weighted() {
      var x =
          Samples.columns(
              "花弁長さ",
              new double[] {0.1, 0.2, 0.3, 0.8, 0.7, 0.9},
              "花弁幅",
              new double[] {0.1, 0.1, 0.1, 0.2, 0.9, 0.9});
      var t = List.of("setosa", "setosa", "setosa", "versicolor", "virginica", "virginica");
      var tree = DecisionTree.unlimited().fit(x, t).tree().orElseThrow();

      var importances = FeatureImportance.treeImportances(tree, x, t);

      assertThat(importances.get("花弁長さ")).isCloseTo(7.0 / 11, within(1e-12));
      assertThat(importances.get("花弁幅")).isCloseTo(4.0 / 11, within(1e-12));
    }
  }

  @Nested
  class ForestImportances {
    @Test
    @DisplayName("木が 1 本なら学習に使った行でのその木の重要度と一致する")
    void singleTree() {
      var x =
          Samples.columns(
              "がく片幅",
              new double[] {0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4},
              "花弁幅",
              new double[] {0.1, 0.12, 0.14, 0.16, 0.8, 0.82, 0.84, 0.86});
      var t =
          List.of(
              "setosa",
              "setosa",
              "setosa",
              "setosa",
              "virginica",
              "virginica",
              "virginica",
              "virginica");
      var forest = RandomForest.of(1, 2, 0).fit(x, t);
      FittedTree fitted = forest.trees().getFirst();

      var expected =
          FeatureImportance.treeImportances(
              fitted.model().tree().orElseThrow(),
              fitted.rows().stream().map(x::get).toList(),
              fitted.rows().stream().map(t::get).toList());

      assertThat(FeatureImportance.forestImportances(forest, x, t)).isEqualTo(expected);
    }
  }
}
