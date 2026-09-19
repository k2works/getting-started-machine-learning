package chapter03;

import static chapter03.Samples.column;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import chapter02.Features;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class DecisionTreeTest {
  @Nested
  class Gini {
    @Test
    @DisplayName("1 種類のラベルだけならジニ不純度は 0")
    void pure() {
      assertThat(DecisionTrees.gini(List.of("Iris-setosa", "Iris-setosa", "Iris-setosa")))
          .isEqualTo(0.0);
    }

    @Test
    @DisplayName("2 種類のラベルが半分ずつならジニ不純度は 0.5")
    void half() {
      assertThat(DecisionTrees.gini(List.of("Iris-setosa", "Iris-virginica"))).isEqualTo(0.5);
    }

    @Test
    @DisplayName("3 種類のラベルが同じ数ならジニ不純度は 3 分の 2")
    void three() {
      assertThat(DecisionTrees.gini(List.of("Iris-setosa", "Iris-versicolor", "Iris-virginica")))
          .isCloseTo(2.0 / 3, within(1e-12));
    }
  }

  @Nested
  class BestSplit {
    @Test
    @DisplayName("ラベルを完全に分けられる境界を見つける")
    void separates() {
      var x = column("花弁幅", 0.1, 0.2, 0.7, 0.8);
      var t = List.of("setosa", "setosa", "virginica", "virginica");

      Split split = DecisionTrees.bestSplit(x, t).orElseThrow();

      assertThat(split.feature()).isEqualTo("花弁幅");
      assertThat(split.threshold()).isCloseTo(0.45, within(1e-12));
      assertThat(split.impurity()).isCloseTo(0.0, within(1e-12));
    }

    @Test
    @DisplayName("複数の特徴量から不純度が最も小さくなる特徴量と境界を選ぶ")
    void choosesBestFeature() {
      var columns = List.of("がく片長さ", "花弁長さ");
      var x =
          List.of(
              new Features(columns, new double[] {0.1, 0.2}),
              new Features(columns, new double[] {0.3, 0.1}),
              new Features(columns, new double[] {0.2, 0.9}),
              new Features(columns, new double[] {0.4, 0.6}));
      var t = List.of("setosa", "setosa", "virginica", "virginica");

      Split split = DecisionTrees.bestSplit(x, t).orElseThrow();

      assertThat(split.feature()).isEqualTo("花弁長さ");
      assertThat(split.threshold()).isCloseTo(0.4, within(1e-12));
    }

    @Test
    @DisplayName("ラベルが 1 種類なら分割しない")
    void noSplitForPureLabels() {
      assertThat(
              DecisionTrees.bestSplit(
                  column("花弁幅", 0.1, 0.2, 0.7), List.of("setosa", "setosa", "setosa")))
          .isEmpty();
    }
  }

  @Nested
  class FitAndPredict {
    @Test
    @DisplayName("1 種類のラベルだけを学習するとそのラベルを予測する")
    void singleLabel() {
      DecisionTree model =
          DecisionTree.unlimited().fit(column("花弁幅", 0.1, 0.2), List.of("setosa", "setosa"));

      assertThat(model.predict(column("花弁幅", 0.15, 0.9))).containsExactly("setosa", "setosa");
    }

    @Test
    @DisplayName("境界の左右で異なるラベルを予測する")
    void leftAndRight() {
      DecisionTree model =
          DecisionTree.unlimited()
              .fit(
                  column("花弁幅", 0.1, 0.2, 0.7, 0.8),
                  List.of("setosa", "setosa", "virginica", "virginica"));

      assertThat(model.predict(column("花弁幅", 0.15, 0.75))).containsExactly("setosa", "virginica");
    }

    @Test
    @DisplayName("深さを制限しなければすべての訓練データを分け切る")
    void unlimitedDepth() {
      DecisionTree model =
          DecisionTree.unlimited().fit(Samples.threeSpeciesX(), Samples.threeSpeciesT());

      assertThat(model.predict(Samples.threeSpeciesX())).isEqualTo(Samples.threeSpeciesT());
    }

    @Test
    @DisplayName("深さを 1 に制限すると境界の先は多数派のラベルを予測する")
    void depthOne() {
      DecisionTree model =
          DecisionTree.withMaxDepth(1).fit(Samples.threeSpeciesX(), Samples.threeSpeciesT());

      assertThat(model.predict(column("花弁幅", 0.2, 0.95))).containsExactly("setosa", "versicolor");
    }

    @Test
    @DisplayName("学習する前に予測するとエラーになる")
    void predictBeforeFit() {
      assertThatThrownBy(() -> DecisionTree.unlimited().predict(column("花弁幅", 0.1)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("fit で学習してから predict を呼んでください");
    }
  }

  @Nested
  class Format {
    @Test
    @DisplayName("葉だけの木はラベルを表示する")
    void leaf() {
      assertThat(DecisionTrees.format(new Leaf("setosa"))).isEqualTo("setosa");
    }

    @Test
    @DisplayName("節は条件ごとに字下げして表示する")
    void node() {
      Tree tree =
          new Node(
              new Split("花弁幅", 0.4, 0.0),
              new Leaf("setosa"),
              new Node(
                  new Split("花弁長さ", 0.75, 0.0), new Leaf("versicolor"), new Leaf("virginica")));

      assertThat(DecisionTrees.format(tree))
          .isEqualTo(
              """
              花弁幅 <= 0.4000
                setosa
              花弁幅 > 0.4000
                花弁長さ <= 0.7500
                  versicolor
                花弁長さ > 0.7500
                  virginica""");
    }
  }
}
