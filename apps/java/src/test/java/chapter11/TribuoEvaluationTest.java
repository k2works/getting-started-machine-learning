package chapter11;

import static chapter11.ModelsTest.features;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import chapter02.Features;
import chapter03.TribuoTrees;
import chapter07.TribuoRegression;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.tribuo.MutableDataset;
import org.tribuo.classification.Label;
import org.tribuo.classification.evaluation.LabelEvaluation;
import org.tribuo.classification.evaluation.LabelEvaluator;
import org.tribuo.evaluation.KFoldSplitter;
import org.tribuo.regression.evaluation.RegressionEvaluator;
import org.tribuo.regression.slm.SLMTrainer;

class TribuoEvaluationTest {
  private static final Label POSITIVE = new Label("1");

  /** Tribuo の CART を、この章の Model として使うテスト用のアダプター */
  static final class TribuoTree implements Model<String> {
    private final int maxDepth;
    private org.tribuo.Model<Label> model;

    TribuoTree(int maxDepth) {
      this.maxDepth = maxDepth;
    }

    @Override
    public void fit(List<Features> x, List<String> t) {
      model = TribuoTrees.train(x, t, maxDepth);
    }

    @Override
    public List<String> predict(List<Features> x) {
      return TribuoTrees.predict(model, x);
    }
  }

  @Nested
  @DisplayName("LabelEvaluator")
  class LabelEvaluatorTest {
    private final List<Features> x = features(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0);
    private final List<String> t = List.of("0", "0", "1", "0", "0", "1", "1", "0", "1", "1");
    private final org.tribuo.Model<Label> model = TribuoTrees.train(x, t, 1);
    private final List<String> predicted = TribuoTrees.predict(model, x);
    private final LabelEvaluation evaluation =
        new LabelEvaluator().evaluate(model, TribuoTrees.toDataset(x, t));

    @Test
    @DisplayName("混同行列が Tribuo の評価器と一致する")
    void confusionMatrix() {
      var cm = ConfusionMatrix.of(t, predicted, "1");

      var tribuo = evaluation.getConfusionMatrix();
      assertThat(
              List.of(
                  tribuo.tp(POSITIVE),
                  tribuo.fp(POSITIVE),
                  tribuo.fn(POSITIVE),
                  tribuo.tn(POSITIVE)))
          .containsExactly((double) cm.tp(), (double) cm.fp(), (double) cm.fn(), (double) cm.tn());
    }

    @Test
    @DisplayName("正解率と適合率と再現率と F 値が Tribuo の評価器と一致する")
    void scores() {
      var cm = ConfusionMatrix.of(t, predicted, "1");

      assertThat(Metrics.accuracy(t, predicted)).isCloseTo(evaluation.accuracy(), within(1e-12));
      assertThat(Metrics.precision(cm)).isCloseTo(evaluation.precision(POSITIVE), within(1e-12));
      assertThat(Metrics.recall(cm)).isCloseTo(evaluation.recall(POSITIVE), within(1e-12));
      assertThat(Metrics.f1Score(cm)).isCloseTo(evaluation.f1(POSITIVE), within(1e-12));
    }

    @Test
    @DisplayName("正例を 1 件も予測しなければ Tribuo の評価器も適合率と再現率と F 値を 0 にする")
    void neverPositive() {
      // 深さ 0 の木は、訓練データの多数派の "0" だけを予測する
      List<String> mostlyNegative = new ArrayList<>(Collections.nCopies(9, "0"));
      mostlyNegative.add("1");
      var neverPositive = TribuoTrees.train(x, mostlyNegative, 0);

      var zero = new LabelEvaluator().evaluate(neverPositive, TribuoTrees.toDataset(x, t));

      assertThat(List.of(zero.precision(POSITIVE), zero.recall(POSITIVE), zero.f1(POSITIVE)))
          .containsExactly(0.0, 0.0, 0.0);
    }
  }

  @Test
  @DisplayName("MSE は Tribuo の RegressionEvaluator の RMSE の 2 乗と一致する")
  void meanSquaredError() {
    var x = features(1, 2, 3, 4, 5, 6);
    var t = List.of(1.1, 2.3, 2.8, 4.4, 4.9, 6.2);
    var model = TribuoRegression.train(new SLMTrainer(true), x, t);

    double rmse =
        new RegressionEvaluator()
            .evaluate(model, TribuoRegression.toDataset(x, t))
            .rmse()
            .values()
            .iterator()
            .next();

    assertThat(Metrics.meanSquaredError(t, TribuoRegression.predict(model, x)))
        .isCloseTo(rmse * rmse, within(1e-9));
  }

  private static List<Integer> tribuoTestSizes(int nSamples, int nSplits) {
    var x = IntStream.range(0, nSamples).mapToObj(i -> features(i).getFirst()).toList();
    var t = IntStream.range(0, nSamples).mapToObj(i -> i < nSamples / 2 ? "0" : "1").toList();
    MutableDataset<Label> dataset = TribuoTrees.toDataset(x, t);
    List<Integer> sizes = new ArrayList<>();
    new KFoldSplitter<Label>(nSplits, 0L)
        .split(dataset, true)
        .forEachRemaining(fold -> sizes.add(fold.test.size()));
    return sizes;
  }

  @Test
  @DisplayName("分割ごとのテストデータの件数が Tribuo の KFoldSplitter と一致する")
  void kFoldSizes() {
    for (int[] sizes : new int[][] {{10, 3}, {7, 2}, {11, 4}}) {
      List<Integer> mine =
          CrossValidation.kFold(sizes[0], sizes[1], 0).stream()
              .map(fold -> fold.test().size())
              .toList();

      assertThat(mine)
          .as("%d 件を %d 分割", sizes[0], sizes[1])
          .isEqualTo(tribuoTestSizes(sizes[0], sizes[1]));
    }
  }

  @Test
  @DisplayName("同じ分割なら Tribuo の評価器で採点した正解率の平均と一致する")
  void sameFolds() {
    var x = features(IntStream.rangeClosed(1, 20).mapToDouble(i -> i * 0.05).toArray());
    var t = IntStream.rangeClosed(1, 20).mapToObj(i -> i % 3 == 0 || i > 12 ? "1" : "0").toList();
    var folds = CrossValidation.kFold(20, 4, 0);

    double tribuo =
        folds.stream()
            .mapToDouble(
                fold -> {
                  var train = pick(x, fold.train());
                  var model = TribuoTrees.train(train, pick(t, fold.train()), 1);
                  var test = TribuoTrees.toDataset(pick(x, fold.test()), pick(t, fold.test()));
                  return new LabelEvaluator().evaluate(model, test).accuracy();
                })
            .average()
            .orElseThrow();

    double mine =
        CrossValidation.crossValidate(() -> new TribuoTree(1), x, t, folds, Metrics::accuracy)
            .average()
            .orElseThrow();
    assertThat(mine).isCloseTo(tribuo, within(1e-12));
  }

  static <E> List<E> pick(List<E> values, List<Integer> positions) {
    return positions.stream().map(values::get).toList();
  }
}
