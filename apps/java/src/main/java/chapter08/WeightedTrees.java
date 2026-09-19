package chapter08;

import chapter02.Features;
import chapter03.Split;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/** 1 件ごとの重みを使って決定木を作り、予測する関数。 */
public final class WeightedTrees {
  private WeightedTrees() {}

  /** 重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。 */
  public static double weightedGini(List<Integer> labels, List<Double> weights) {
    double total = sum(weights);
    return 1.0
        - weightSums(labels, weights).values().stream()
            .mapToDouble(weight -> Math.pow(weight / total, 2))
            .sum();
  }

  /** ラベルごとの重みの合計を、ラベルが先に現れた順に並べて返す。 */
  private static Map<Integer, Double> weightSums(List<Integer> labels, List<Double> weights) {
    Map<Integer, Double> sums = new LinkedHashMap<>();
    for (int i = 0; i < labels.size(); i++) {
      sums.merge(labels.get(i), weights.get(i), Double::sum);
    }
    return sums;
  }

  /** クラスの件数に反比例する重み（件数 / (クラスの数 × そのクラスの件数)）を 1 件ごとに求める。 */
  public static List<Double> balancedWeights(List<Integer> t) {
    Map<Integer, Integer> counts = new LinkedHashMap<>();
    t.forEach(label -> counts.merge(label, 1, Integer::sum));
    return t.stream()
        .map(label -> (double) t.size() / (counts.size() * counts.get(label)))
        .toList();
  }

  /** 重みの合計が最も大きいラベル。同じなら先に現れたラベルを選ぶ。 */
  static int weightedMajority(List<Integer> labels, List<Double> weights) {
    int best = labels.getFirst();
    double bestWeight = 0.0;
    for (Map.Entry<Integer, Double> entry : weightSums(labels, weights).entrySet()) {
      if (entry.getValue() > bestWeight) {
        best = entry.getKey();
        bestWeight = entry.getValue();
      }
    }
    return best;
  }

  /** 左右の重み付き不純度の、重みによる平均が最も小さくなる分割。同じ不純度なら先に見つけた分割を選ぶ。 */
  static Optional<Split> bestSplit(List<Features> x, List<Integer> t, List<Double> w) {
    if (weightedGini(t, w) == 0.0) {
      return Optional.empty();
    }
    Split best = null;
    for (String feature : x.getFirst().columns()) {
      List<Integer> order =
          IntStream.range(0, x.size())
              .boxed()
              .sorted(Comparator.comparingDouble(i -> x.get(i).value(feature)))
              .toList();
      List<Double> values = order.stream().map(i -> x.get(i).value(feature)).toList();
      List<Integer> labels = order.stream().map(t::get).toList();
      List<Double> weights = order.stream().map(w::get).toList();
      for (int i = 1; i < order.size(); i++) {
        if (values.get(i).equals(values.get(i - 1))) {
          continue;
        }
        List<Double> left = weights.subList(0, i);
        List<Double> right = weights.subList(i, weights.size());
        double impurity =
            (sum(left) * weightedGini(labels.subList(0, i), left)
                    + sum(right) * weightedGini(labels.subList(i, labels.size()), right))
                / sum(weights);
        if (best == null || impurity < best.impurity()) {
          best = new Split(feature, (values.get(i - 1) + values.get(i)) / 2, impurity);
        }
      }
    }
    return Optional.ofNullable(best);
  }

  /** 深さの上限まで分割を繰り返して木を作る。maxDepth が負なら上限なし。 */
  static TreeNode build(List<Features> x, List<Integer> t, List<Double> w, int maxDepth) {
    Optional<Split> split = maxDepth == 0 ? Optional.empty() : bestSplit(x, t, w);
    if (split.isEmpty()) {
      return new LeafNode(weightedMajority(t, w));
    }
    Split s = split.get();
    List<Integer> left =
        IntStream.range(0, x.size())
            .filter(i -> x.get(i).value(s.feature()) <= s.threshold())
            .boxed()
            .toList();
    List<Integer> right =
        IntStream.range(0, x.size())
            .filter(i -> x.get(i).value(s.feature()) > s.threshold())
            .boxed()
            .toList();
    int childDepth = maxDepth < 0 ? maxDepth : maxDepth - 1;
    return new SplitNode(
        s.feature(),
        s.threshold(),
        build(pick(x, left), pick(t, left), pick(w, left), childDepth),
        build(pick(x, right), pick(t, right), pick(w, right), childDepth));
  }

  private static <E> List<E> pick(List<E> values, List<Integer> positions) {
    return positions.stream().map(values::get).toList();
  }

  /** 1 件の特徴量のラベルを予測する。 */
  static int predictOne(TreeNode node, Features features) {
    return switch (node) {
      case LeafNode leaf -> leaf.label();
      case SplitNode split ->
          features.value(split.feature()) <= split.threshold()
              ? predictOne(split.left(), features)
              : predictOne(split.right(), features);
    };
  }

  private static double sum(List<Double> values) {
    return values.stream().mapToDouble(Double::doubleValue).sum();
  }
}
