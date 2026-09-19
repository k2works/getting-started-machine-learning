package chapter01;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KinokoTakenokoTest {
  @Nested
  class SplitFeaturesAndLabels {
    @Test
    @DisplayName("人物のリストを特徴量と正解ラベルに分ける")
    void splitsPeople() {
      var people = List.of(new Person(161, 52, 20, "きのこ"), new Person(183, 74, 50, "たけのこ"));

      FeaturesAndLabels split = KinokoTakenoko.splitFeaturesAndLabels(people);

      assertThat(split.features())
          .containsExactly(new Features(161, 52, 20), new Features(183, 74, 50));
      assertThat(split.labels()).containsExactly("きのこ", "たけのこ");
    }
  }

  @Nested
  class PredictByRule {
    @Test
    @DisplayName("20 代ならきのこ派と判定する")
    void twentiesAreKinoko() {
      assertThat(KinokoTakenoko.predictByRule(new Features(161, 52, 20))).isEqualTo("きのこ");
    }

    @Test
    @DisplayName("20 代以外ならたけのこ派と判定する")
    void othersAreTakenoko() {
      assertThat(KinokoTakenoko.predictByRule(new Features(183, 74, 50))).isEqualTo("たけのこ");
    }
  }

  @Nested
  class Accuracy {
    @Test
    @DisplayName("すべての予測が正解なら正解率は 1")
    void allCorrect() {
      assertThat(KinokoTakenoko.accuracy(List.of("きのこ", "たけのこ"), List.of("きのこ", "たけのこ")))
          .isEqualTo(1.0);
    }

    @Test
    @DisplayName("4 件中 3 件の予測が正解なら正解率は 0.75")
    void threeOfFour() {
      var predictions = List.of("きのこ", "きのこ", "たけのこ", "たけのこ");
      var labels = List.of("きのこ", "たけのこ", "たけのこ", "たけのこ");

      assertThat(KinokoTakenoko.accuracy(predictions, labels)).isEqualTo(0.75);
    }

    @Test
    @DisplayName("予測と正解ラベルの件数が違えばエラーになる")
    void sizeMismatch() {
      assertThatThrownBy(() -> KinokoTakenoko.accuracy(List.of("きのこ"), List.of("きのこ", "たけのこ")))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
