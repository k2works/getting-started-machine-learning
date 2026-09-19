package chapter03;

import static chapter03.Samples.column;
import static org.assertj.core.api.Assertions.assertThat;

import chapter02.Features;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;

class TribuoTreesTest {
  @Test
  @DisplayName("特徴量を特徴量名つきの事例に変換する")
  void toDataset() {
    var columns = List.of("花弁長さ", "花弁幅");
    var x =
        List.of(
            new Features(columns, new double[] {0.1, 0.2}),
            new Features(columns, new double[] {0.6, 0.8}));

    MutableDataset<Label> dataset = TribuoTrees.toDataset(x, List.of("setosa", "virginica"));

    assertThat(dataset.size()).isEqualTo(2);
    assertThat(dataset.getFeatureIDMap().keySet()).containsExactlyInAnyOrder("花弁長さ", "花弁幅");
    assertThat(dataset.getOutputInfo().getDomain())
        .extracting(Label::getLabel)
        .containsExactlyInAnyOrder("setosa", "virginica");
  }

  @Test
  @DisplayName("分割候補や多数決が同じにならなければ Tribuo の CART と自作の決定木は同じ予測をする")
  void sameAsMine() {
    var x = Samples.threeSpeciesX();
    var t = Samples.threeSpeciesT();
    var newX = column("花弁幅", 0.2, 0.4, 0.55, 0.75, 0.95);

    var tribuo = TribuoTrees.predict(TribuoTrees.train(x, t, TribuoTrees.UNLIMITED), newX);

    assertThat(tribuo).isEqualTo(DecisionTree.unlimited().fit(x, t).predict(newX));
  }

  @Test
  @DisplayName("葉の多数決が同数のとき自作は先に現れたラベルを選ぶが Tribuo は出現順に依存しない")
  void majorityTie() {
    var x = column("花弁幅", 0.1, 0.1);
    var orders = List.of(List.of("b", "a"), List.of("a", "b"));

    var mine = orders.stream().map(t -> DecisionTree.unlimited().fit(x, t).predict(x).getFirst());
    var tribuo =
        orders.stream()
            .map(
                t ->
                    TribuoTrees.predict(TribuoTrees.train(x, t, TribuoTrees.UNLIMITED), x)
                        .getFirst())
            .distinct();

    assertThat(mine).containsExactly("b", "a");
    assertThat(tribuo).hasSize(1);
  }

  @Test
  @DisplayName("同じ不純度の分割候補が複数あるとき自作は列の順で選ぶが Tribuo は特徴量名の順で選ぶ")
  void impurityTie() {
    var columns = List.of("b", "a");
    var x =
        List.of(
            new Features(columns, new double[] {0.1, 0.1}),
            new Features(columns, new double[] {0.9, 0.9}));
    var t = List.of("left", "right");
    var newX = List.of(new Features(columns, new double[] {0.2, 0.8}));

    assertThat(DecisionTree.unlimited().fit(x, t).predict(newX)).containsExactly("left");
    assertThat(TribuoTrees.predict(TribuoTrees.train(x, t, TribuoTrees.UNLIMITED), newX))
        .containsExactly("right");
  }
}
