package chapter11;

import chapter02.Features;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

/** K 分割交差検証。 */
public final class CrossValidation {
  private CrossValidation() {}

  /**
   * シード付きの乱数で行を並べ替え、nSplits 個のテストデータに分ける。
   *
   * <p>件数が割り切れないときは、余りを先頭の分割から 1 件ずつ配る。
   */
  public static List<Fold> kFold(int nSamples, int nSplits, long seed) {
    List<Integer> positions = new ArrayList<>(IntStream.range(0, nSamples).boxed().toList());
    Collections.shuffle(positions, new Random(seed));
    List<Fold> folds = new ArrayList<>();
    int from = 0;
    for (int i = 0; i < nSplits; i++) {
      int to = from + nSamples / nSplits + (i < nSamples % nSplits ? 1 : 0);
      List<Integer> test = positions.subList(from, to);
      Set<Integer> testSet = new HashSet<>(test);
      List<Integer> train = positions.stream().filter(p -> !testSet.contains(p)).toList();
      folds.add(new Fold(train, test));
      from = to;
    }
    return List.copyOf(folds);
  }

  /**
   * 分割ごとに新しいモデルを作って訓練データで学習し、テストデータの予測を評価関数で採点する。
   *
   * <p>スコアは遅延評価の DoubleStream で返す。取り出した分だけ学習し、ストリームは 1 度しか使えない。
   */
  public static <T> DoubleStream crossValidate(
      Supplier<? extends Model<T>> makeModel,
      List<Features> x,
      List<T> t,
      List<Fold> folds,
      Metric<T> metric) {
    return folds.stream()
        .mapToDouble(
            fold -> {
              Model<T> model = makeModel.get();
              model.fit(pick(x, fold.train()), pick(t, fold.train()));
              return metric.applyAsDouble(
                  pick(t, fold.test()), model.predict(pick(x, fold.test())));
            });
  }

  private static <E> List<E> pick(List<E> values, List<Integer> positions) {
    return positions.stream().map(values::get).toList();
  }
}
