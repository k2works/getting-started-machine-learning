package chapter03;

import chapter02.Features;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

/** 決定木を作り、予測し、表示する関数。 */
public final class DecisionTrees {
  private DecisionTrees() {}

  /** ジニ不純度。ラベルが 1 種類なら 0 で、種類が多く均等なほど大きい。 */
  public static double gini(List<String> labels) {
    double total = labels.size();
    return 1.0
        - counts(labels).values().stream().mapToDouble(count -> Math.pow(count / total, 2)).sum();
  }

  /** ラベルごとの件数を、ラベルが先に現れた順に並べて返す。 */
  private static Map<String, Integer> counts(List<String> labels) {
    Map<String, Integer> counts = new LinkedHashMap<>();
    labels.forEach(label -> counts.merge(label, 1, Integer::sum));
    return counts;
  }

  /** 多数派のラベル。同数なら先に現れたラベルを選ぶ。 */
  static String majority(List<String> labels) {
    String best = null;
    int bestCount = 0;
    for (Map.Entry<String, Integer> entry : counts(labels).entrySet()) {
      if (entry.getValue() > bestCount) {
        best = entry.getKey();
        bestCount = entry.getValue();
      }
    }
    return best;
  }

  /** 左右の不純度の重み付き平均が最も小さくなる分割。同じ不純度なら先に見つけた（列の順で前の）分割を選ぶ。 */
  public static Optional<Split> bestSplit(List<Features> x, List<String> t) {
    if (gini(t) == 0.0) {
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
      List<String> labels = order.stream().map(t::get).toList();
      for (int i = 1; i < order.size(); i++) {
        if (values.get(i).equals(values.get(i - 1))) {
          continue;
        }
        List<String> left = labels.subList(0, i);
        List<String> right = labels.subList(i, labels.size());
        double impurity = (left.size() * gini(left) + right.size() * gini(right)) / order.size();
        if (best == null || impurity < best.impurity()) {
          best = new Split(feature, (values.get(i - 1) + values.get(i)) / 2, impurity);
        }
      }
    }
    return Optional.ofNullable(best);
  }

  /** 深さの上限まで分割を繰り返して木を作る。maxDepth が負なら上限なし。 */
  static Tree build(List<Features> x, List<String> t, int maxDepth) {
    Optional<Split> split = maxDepth == 0 ? Optional.empty() : bestSplit(x, t);
    if (split.isEmpty()) {
      return new Leaf(majority(t));
    }
    Split s = split.get();
    List<Integer> left =
        IntStream.range(0, x.size()).filter(i -> goesLeft(s, x.get(i))).boxed().toList();
    List<Integer> right =
        IntStream.range(0, x.size()).filter(i -> !goesLeft(s, x.get(i))).boxed().toList();
    int childDepth = maxDepth < 0 ? maxDepth : maxDepth - 1;
    return new Node(
        s,
        build(pick(x, left), pick(t, left), childDepth),
        build(pick(x, right), pick(t, right), childDepth));
  }

  private static boolean goesLeft(Split split, Features features) {
    return features.value(split.feature()) <= split.threshold();
  }

  private static <E> List<E> pick(List<E> values, List<Integer> positions) {
    return positions.stream().map(values::get).toList();
  }

  /** 1 件の特徴量のラベルを予測する。 */
  public static String predictOne(Tree tree, Features features) {
    return switch (tree) {
      case Leaf leaf -> leaf.label();
      case Node node ->
          goesLeft(node.split(), features)
              ? predictOne(node.left(), features)
              : predictOne(node.right(), features);
    };
  }

  /** 木を、条件ごとに字下げした文字列にする。 */
  public static String format(Tree tree) {
    return format(tree, "");
  }

  private static String format(Tree tree, String indent) {
    return switch (tree) {
      case Leaf leaf -> indent + leaf.label();
      case Node node -> {
        String feature = node.split().feature();
        String threshold = String.format(Locale.ROOT, "%.4f", node.split().threshold());
        yield String.join(
            "\n",
            indent + feature + " <= " + threshold,
            format(node.left(), indent + "  "),
            indent + feature + " > " + threshold,
            format(node.right(), indent + "  "));
      }
    };
  }
}
