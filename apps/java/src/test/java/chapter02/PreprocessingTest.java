package chapter02;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PreprocessingTest {
  private static Row row(String sepalLength, String sepalWidth) {
    return new Row(Map.of("がく片長さ", sepalLength, "がく片幅", sepalWidth));
  }

  @Nested
  class ColumnMeans {
    @Test
    @DisplayName("欠損値を除いて列ごとの平均値を求める")
    void ignoresMissing() {
      var rows = List.of(row("0.1", "0.2"), row("", "0.4"), row("0.3", "0.9"));

      Map<String, Double> means = Preprocessing.columnMeans(rows, List.of("がく片長さ", "がく片幅"));

      assertThat(means.get("がく片長さ")).isCloseTo(0.2, within(1e-12));
      assertThat(means.get("がく片幅")).isCloseTo(0.5, within(1e-12));
    }
  }

  @Nested
  class FillMissing {
    @Test
    @DisplayName("欠損値を列ごとに指定した値で補完して特徴量にする")
    void fillsWithGivenValues() {
      var rows = List.of(row("0.1", ""), row("", "0.4"));
      var columns = List.of("がく片長さ", "がく片幅");

      List<Features> filled =
          Preprocessing.fillMissing(rows, columns, Map.of("がく片長さ", 0.2, "がく片幅", 0.5));

      assertThat(filled)
          .containsExactly(
              new Features(columns, new double[] {0.1, 0.5}),
              new Features(columns, new double[] {0.2, 0.4}));
    }

    @Test
    @DisplayName("元の行は変更しない")
    void keepsOriginalRows() {
      var rows = List.of(row("", "0.2"));

      Preprocessing.fillMissing(rows, List.of("がく片長さ", "がく片幅"), Map.of("がく片長さ", 0.2));

      assertThat(rows.getFirst().isMissing("がく片長さ")).isTrue();
    }
  }

  @Nested
  class FeaturesRecord {
    @Test
    @DisplayName("値の配列を写して持ち、渡した配列を後から変えても影響を受けない")
    void copiesValues() {
      double[] values = {0.1, 0.2};
      Features features = new Features(List.of("a", "b"), values);

      values[0] = 9.9;

      assertThat(features.value("a")).isEqualTo(0.1);
    }

    @Test
    @DisplayName("列名と値が同じなら等しい")
    void equalByValue() {
      assertThat(new Features(List.of("a"), new double[] {0.1}))
          .isEqualTo(new Features(List.of("a"), new double[] {0.1}))
          .hasSameHashCodeAs(new Features(List.of("a"), new double[] {0.1}));
    }
  }

  @Nested
  class SplitFeaturesAndTarget {
    @Test
    @DisplayName("特徴量の列と正解ラベルの列に分ける")
    void splitsColumns() {
      Table table =
          new Table(
              List.of("がく片長さ", "花弁幅", "種類"),
              List.of(
                  new Row(Map.of("がく片長さ", "0.1", "花弁幅", "0.4", "種類", "Iris-setosa")),
                  new Row(Map.of("がく片長さ", "0.5", "花弁幅", "0.8", "種類", "Iris-virginica"))));

      FeaturesAndTarget split = Preprocessing.splitFeaturesAndTarget(table, "種類");

      assertThat(split.columns()).containsExactly("がく片長さ", "花弁幅");
      assertThat(split.rows()).isEqualTo(table.rows());
      assertThat(split.target()).containsExactly("Iris-setosa", "Iris-virginica");
    }
  }

  @Nested
  class SplitTrainTest {
    private final List<Integer> x = IntStream.range(0, 10).boxed().toList();
    private final List<String> t = x.stream().map(i -> "label" + i).toList();

    @Test
    @DisplayName("テストデータの割合どおりの件数に分ける")
    void splitsByRatio() {
      var split = Preprocessing.splitTrainTest(x, t, 0.3, 0);

      assertThat(List.of(split.xTrain().size(), split.xTest().size())).containsExactly(7, 3);
      assertThat(List.of(split.tTrain().size(), split.tTest().size())).containsExactly(7, 3);
    }

    @Test
    @DisplayName("件数が変わってもテストデータの割合どおりに分ける")
    void splitsOtherSizes() {
      var twenty = IntStream.range(0, 20).boxed().toList();

      var split = Preprocessing.splitTrainTest(twenty, twenty, 0.25, 0);

      assertThat(List.of(split.xTrain().size(), split.xTest().size())).containsExactly(15, 5);
    }

    @Test
    @DisplayName("すべての行を重複なく訓練データとテストデータのどちらかに入れる")
    void coversAllRowsOnce() {
      var split = Preprocessing.splitTrainTest(x, t, 0.3, 0);

      Set<Integer> train = new HashSet<>(split.xTrain());
      Set<Integer> test = new HashSet<>(split.xTest());
      assertThat(train).doesNotContainAnyElementsOf(test);
      Set<Integer> all = new HashSet<>(train);
      all.addAll(test);
      assertThat(all).containsExactlyInAnyOrderElementsOf(x);
    }

    @Test
    @DisplayName("特徴量と正解ラベルの対応を保ったまま分ける")
    void keepsPairs() {
      var split = Preprocessing.splitTrainTest(x, t, 0.3, 0);

      assertThat(split.tTrain()).isEqualTo(split.xTrain().stream().map(i -> "label" + i).toList());
      assertThat(split.tTest()).isEqualTo(split.xTest().stream().map(i -> "label" + i).toList());
    }

    @Test
    @DisplayName("同じシードなら同じ分け方になる")
    void sameSeedSameSplit() {
      assertThat(Preprocessing.splitTrainTest(x, t, 0.3, 42).tTest())
          .isEqualTo(Preprocessing.splitTrainTest(x, t, 0.3, 42).tTest());
    }

    @Test
    @DisplayName("シードが違えば違う分け方になる")
    void differentSeedDifferentSplit() {
      assertThat(Preprocessing.splitTrainTest(x, t, 0.3, 0).tTest())
          .isNotEqualTo(Preprocessing.splitTrainTest(x, t, 0.3, 1).tTest());
    }

    @Test
    @DisplayName("数値の正解ラベルも特徴量との対応を保ったまま分ける")
    void numericLabels() {
      List<Double> numeric = x.stream().map(i -> i * 0.5).toList();

      var split = Preprocessing.splitTrainTest(x, numeric, 0.3, 0);

      assertThat(split.tTest()).isEqualTo(split.xTest().stream().map(i -> i * 0.5).toList());
    }
  }

  @Nested
  class PrepareIris {
    @TempDir Path directory;

    @Test
    @DisplayName("訓練データとテストデータのどちらにも欠損値が残らない")
    void noMissingAfterPreparation() throws IOException {
      Path csvFile =
          Files.writeString(
              directory.resolve("iris.csv"),
              """
              \uFEFFがく片長さ,がく片幅,花弁長さ,花弁幅,種類
              0.1,,0.3,0.4,Iris-setosa
              0.2,0.3,,0.5,Iris-setosa
              ,0.4,0.5,0.6,Iris-virginica
              0.4,0.5,0.6,,Iris-virginica
              """,
              StandardCharsets.UTF_8);

      TrainTestSplit<Features, String> split = Preprocessing.prepareIris(csvFile, 0.5, 0);

      assertThat(split.xTrain()).hasSize(2);
      assertThat(split.xTest()).hasSize(2);
      assertThat(split.xTrain()).allSatisfy(f -> assertThat(f.values()).doesNotContain(Double.NaN));
      assertThat(split.xTrain().getFirst().columns())
          .containsExactly("がく片長さ", "がく片幅", "花弁長さ", "花弁幅");
    }
  }
}
