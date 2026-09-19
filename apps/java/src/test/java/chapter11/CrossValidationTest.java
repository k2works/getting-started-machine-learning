package chapter11;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import chapter02.Features;
import chapter07.RegressionMetrics;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CrossValidationTest {
  private static List<Integer> testSizes(List<Fold> folds) {
    return folds.stream().map(fold -> fold.test().size()).toList();
  }

  @Nested
  @DisplayName("K 分割")
  class KFoldTest {
    @Test
    @DisplayName("データを k 個のテストデータにほぼ均等に分ける")
    void evenSizes() {
      assertThat(testSizes(CrossValidation.kFold(10, 3, 0))).containsExactly(4, 3, 3);
    }

    @Test
    @DisplayName("件数と分割数が変わってもほぼ均等に分ける")
    void otherSizes() {
      assertThat(testSizes(CrossValidation.kFold(7, 2, 0))).containsExactly(4, 3);
    }

    @Test
    @DisplayName("どの行もちょうど一度だけテストデータになる")
    void everyRowOnce() {
      List<Integer> tested =
          CrossValidation.kFold(10, 3, 0).stream().flatMap(fold -> fold.test().stream()).toList();

      assertThat(tested)
          .containsExactlyInAnyOrderElementsOf(IntStream.range(0, 10).boxed().toList());
    }

    @Test
    @DisplayName("各分割の訓練データはテストデータ以外のすべての行")
    void trainIsTheRest() {
      for (Fold fold : CrossValidation.kFold(10, 3, 0)) {
        Set<Integer> all = new HashSet<>(fold.train());
        all.addAll(fold.test());

        assertThat(Collections.disjoint(fold.train(), fold.test())).isTrue();
        assertThat(all).hasSize(10);
      }
    }

    @Test
    @DisplayName("同じシードなら同じ分け方になる")
    void sameSeed() {
      assertThat(CrossValidation.kFold(10, 3, 42)).isEqualTo(CrossValidation.kFold(10, 3, 42));
    }

    @Test
    @DisplayName("シードが違えば違う分け方になる")
    void differentSeed() {
      assertThat(CrossValidation.kFold(10, 3, 0)).isNotEqualTo(CrossValidation.kFold(10, 3, 1));
    }
  }

  /** 訓練データの正解の平均値を常に予測するテスト用のモデル */
  static final class MeanModel implements Model<Double> {
    private double mean;

    @Override
    public void fit(List<Features> x, List<Double> t) {
      mean = t.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    @Override
    public List<Double> predict(List<Features> x) {
      return Collections.nCopies(x.size(), mean);
    }
  }

  @Nested
  @DisplayName("交差検証")
  class CrossValidateTest {
    private final List<Features> x =
        DoubleStream.of(10, 20, 30, 40)
            .mapToObj(v -> new Features(List.of("feature"), new double[] {v}))
            .toList();
    private final List<Double> t = List.of(1.0, 2.0, 3.0, 4.0);
    private final List<Fold> folds =
        List.of(new Fold(List.of(0, 1), List.of(2, 3)), new Fold(List.of(2, 3), List.of(0, 1)));

    @Test
    @DisplayName("分割ごとに訓練データで学習してテストデータを評価する")
    void scores() {
      DoubleStream scores =
          CrossValidation.crossValidate(
              MeanModel::new, x, t, folds, RegressionMetrics::meanAbsoluteError);

      assertThat(scores.toArray()).containsExactly(2.0, 2.0);
    }

    @Test
    @DisplayName("評価関数を差し替えると別の指標で評価する")
    void anotherMetric() {
      DoubleStream scores =
          CrossValidation.crossValidate(MeanModel::new, x, t, folds, Metrics::meanSquaredError);

      assertThat(scores.toArray()).containsExactly(4.25, 4.25);
    }

    @Test
    @DisplayName("最初の分割のスコアだけを取り出すなら学習は 1 回で済む")
    void lazy() {
      var created = new AtomicInteger();
      Supplier<MeanModel> makeModel =
          () -> {
            created.incrementAndGet();
            return new MeanModel();
          };

      OptionalDouble first =
          CrossValidation.crossValidate(
                  makeModel, x, t, folds, RegressionMetrics::meanAbsoluteError)
              .findFirst();

      assertThat(first).hasValue(2.0);
      assertThat(created.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("ストリームは 1 度しか使えない")
    void onlyOnce() {
      DoubleStream scores =
          CrossValidation.crossValidate(
              MeanModel::new, x, t, folds, RegressionMetrics::meanAbsoluteError);
      assertThat(scores.sum()).isEqualTo(4.0);

      assertThatThrownBy(scores::sum)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("stream has already been operated upon or closed");
    }
  }
}
