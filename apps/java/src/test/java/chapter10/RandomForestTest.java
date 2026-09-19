package chapter10;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RandomForestTest {
  @Nested
  class MajorityVote {
    @Test
    @DisplayName("サンプルごとに最も多い予測を選ぶ")
    void mostCommon() {
      var votes =
          List.of(
              List.of("setosa", "virginica"),
              List.of("setosa", "virginica"),
              List.of("versicolor", "setosa"));

      assertThat(RandomForest.majorityVote(votes)).containsExactly("setosa", "virginica");
    }
  }

  @Nested
  class BootstrapSample {
    @Test
    @DisplayName("元のデータと同じ件数の行番号を重複を許して選ぶ")
    void withReplacement() {
      List<Integer> rows = RandomForest.bootstrapSample(100, new Random(0));

      assertThat(rows).hasSize(100).allMatch(row -> row >= 0 && row < 100);
      assertThat(new HashSet<>(rows)).hasSizeLessThan(100);
    }

    @Test
    @DisplayName("同じシードなら同じ行を選ぶ")
    void sameSeed() {
      assertThat(RandomForest.bootstrapSample(10, new Random(42)))
          .isEqualTo(RandomForest.bootstrapSample(10, new Random(42)));
    }
  }

  @Nested
  class FitAndPredict {
    @Test
    @DisplayName("指定した数だけ第 3 章の決定木を学習する")
    void numberOfTrees() {
      var model = RandomForest.of(5, 1, 0).fit(Samples.twoSpeciesX(), Samples.twoSpeciesT());

      assertThat(model.trees()).hasSize(5);
    }

    @Test
    @DisplayName("各決定木は指定した数の特徴量だけを使う")
    void featuresPerTree() {
      var model = RandomForest.of(5, 1, 0).fit(Samples.twoSpeciesX(), Samples.twoSpeciesT());

      assertThat(model.trees()).allMatch(tree -> tree.columns().size() == 1);
    }

    @Test
    @DisplayName("決定木の多数決で予測する")
    void vote() {
      var model = RandomForest.of(25, 2, 0).fit(Samples.twoSpeciesX(), Samples.twoSpeciesT());

      assertThat(model.predict(Samples.twoSpeciesNewX())).containsExactly("setosa", "virginica");
    }

    @Test
    @DisplayName("同じシードなら同じ予測になる")
    void sameSeed() {
      var first = RandomForest.of(5, 1, 7).fit(Samples.twoSpeciesX(), Samples.twoSpeciesT());
      var second = RandomForest.of(5, 1, 7).fit(Samples.twoSpeciesX(), Samples.twoSpeciesT());

      assertThat(second.trees())
          .extracting(FittedTree::columns)
          .isEqualTo(first.trees().stream().map(FittedTree::columns).toList());
      assertThat(second.predict(Samples.twoSpeciesX()))
          .isEqualTo(first.predict(Samples.twoSpeciesX()));
    }

    @Test
    @DisplayName("学習する前に予測するとエラーになる")
    void predictBeforeFit() {
      assertThatThrownBy(() -> RandomForest.of(10, 2, 0).predict(Samples.column("花弁幅", 0.1)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("fit で学習してから predict を呼んでください");
    }
  }
}
