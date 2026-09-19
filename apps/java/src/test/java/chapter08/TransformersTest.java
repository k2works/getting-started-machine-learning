package chapter08;

import static chapter08.Tables.numbers;
import static chapter08.Tables.table;
import static chapter08.Tables.texts;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class TransformersTest {
  @Nested
  @DisplayName("GroupMedianImputer")
  class GroupMedianImputerTest {
    private final GroupMedianImputer imputer =
        new GroupMedianImputer("Age", List.of("Pclass", "Sex"));

    @Test
    @DisplayName("同じグループの中央値で欠損値を補完する")
    void sameGroup() {
      var x = table("Pclass,Sex,Age", "1,female,20", "1,female,30", "1,female,70", "1,female,");

      var filled = imputer.fit(x).transform(x);

      assertThat(numbers(filled, "Age")).containsExactly(20.0, 30.0, 70.0, 30.0);
    }

    @Test
    @DisplayName("グループごとに異なる中央値で補完する")
    void differentGroups() {
      var x =
          table(
              "Pclass,Sex,Age",
              "1,female,40",
              "1,female,50",
              "1,female,",
              "3,male,10",
              "3,male,20",
              "3,male,");

      var filled = imputer.fit(x).transform(x);

      assertThat(numbers(filled, "Age")).containsExactly(40.0, 50.0, 45.0, 10.0, 20.0, 15.0);
    }

    @Test
    @DisplayName("訓練データで求めた中央値を別のデータの補完に使う")
    void fitOnTrain() {
      var train = table("Pclass,Sex,Age", "2,male,30", "2,male,34");
      var other = table("Pclass,Sex,Age", "2,male,");

      var filled = imputer.fit(train).transform(other);

      assertThat(numbers(filled, "Age")).containsExactly(32.0);
    }

    @Test
    @DisplayName("訓練データに無いグループは全体の中央値で補完する")
    void unseenGroup() {
      var train = table("Pclass,Sex,Age", "1,female,30", "1,female,40", "3,male,20");
      var other = table("Pclass,Sex,Age", "2,female,");

      var filled = imputer.fit(train).transform(other);

      assertThat(numbers(filled, "Age")).containsExactly(30.0);
    }

    @Test
    @DisplayName("年齢がすべて欠けたグループは全体の中央値で補完する")
    void allMissingGroup() {
      var x = table("Pclass,Sex,Age", "1,female,30", "1,female,40", "2,female,");

      var filled = imputer.fit(x).transform(x);

      assertThat(numbers(filled, "Age")).containsExactly(30.0, 40.0, 35.0);
    }

    @Test
    @DisplayName("元の表は変更しない")
    void keepsOriginal() {
      var x = table("Pclass,Sex,Age", "1,male,30", "1,male,");

      imputer.fit(x).transform(x);

      assertThat(x.rows().get(1).isMissing("Age")).isTrue();
    }
  }

  @Nested
  @DisplayName("MostFrequentImputer")
  class MostFrequentImputerTest {
    private final MostFrequentImputer imputer = new MostFrequentImputer("Embarked");

    @Test
    @DisplayName("訓練データで最も多い値で欠損値を補完する")
    void mostFrequent() {
      var train = table("Embarked", "S", "C", "S", "");
      var other = table("Embarked", "", "Q");

      var filled = imputer.fit(train).transform(other);

      assertThat(texts(filled, "Embarked")).containsExactly("S", "Q");
    }
  }

  @Nested
  @DisplayName("DummyEncoder")
  class DummyEncoderTest {
    @Test
    @DisplayName("2 値のカテゴリを、最初のカテゴリを除いた 0 と 1 の列にする")
    void twoCategories() {
      var x = table("Pclass,Sex", "1,female", "3,male", "2,male");
      var encoder = new DummyEncoder(List.of("Sex"));

      var encoded = encoder.fit(x).transform(x);

      assertThat(encoded.columns()).containsExactly("Pclass", "Sex_male");
      assertThat(numbers(encoded, "Sex_male")).containsExactly(0.0, 1.0, 1.0);
    }

    @Test
    @DisplayName("別のデータにも訓練データと同じ列を作る")
    void sameColumns() {
      var train = table("Embarked", "C", "Q", "S");
      var other = table("Embarked", "S", "S");
      var encoder = new DummyEncoder(List.of("Embarked"));

      var encoded = encoder.fit(train).transform(other);

      assertThat(encoded.columns()).containsExactly("Embarked_Q", "Embarked_S");
      assertThat(numbers(encoded, "Embarked_Q")).containsExactly(0.0, 0.0);
      assertThat(numbers(encoded, "Embarked_S")).containsExactly(1.0, 1.0);
    }
  }
}
