package chapter10;

import chapter02.Features;
import chapter03.DecisionTrees;
import chapter03.Leaf;
import chapter03.Node;
import chapter03.Split;
import chapter03.Tree;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/** 分割で減った不純度から、特徴量の重要度を求める。 */
public final class FeatureImportance {
  private FeatureImportance() {}

  /** 1 回の分割で減った不純度（件数で重み付け）。 */
  private record Decrease(String feature, double amount) {}

  private static Stream<Decrease> impurityDecreases(Tree tree, List<Features> x, List<String> t) {
    return switch (tree) {
      case Leaf ignored -> Stream.empty();
      case Node node -> {
        Split split = node.split();
        List<Integer> left =
            IntStream.range(0, x.size())
                .filter(i -> x.get(i).value(split.feature()) <= split.threshold())
                .boxed()
                .toList();
        List<Integer> right =
            IntStream.range(0, x.size()).filter(i -> !left.contains(i)).boxed().toList();
        var here =
            new Decrease(split.feature(), t.size() * (DecisionTrees.gini(t) - split.impurity()));
        yield Stream.concat(
            Stream.of(here),
            Stream.concat(
                impurityDecreases(node.left(), pick(x, left), pick(t, left)),
                impurityDecreases(node.right(), pick(x, right), pick(t, right))));
      }
    };
  }

  private static <E> List<E> pick(List<E> values, List<Integer> positions) {
    return positions.stream().map(values::get).toList();
  }

  /** 決定木 1 本の重要度。特徴量の列の順に並べ、合計が 1 になるようにする。 */
  public static Map<String, Double> treeImportances(Tree tree, List<Features> x, List<String> t) {
    Map<String, Double> totals = zeros(x.getFirst().columns());
    impurityDecreases(tree, x, t).forEach(d -> totals.merge(d.feature(), d.amount(), Double::sum));
    return normalize(totals);
  }

  /** 木ごとの重要度の平均。木が使わなかった特徴量は、その木では 0 とする。 */
  public static Map<String, Double> forestImportances(
      RandomForest forest, List<Features> x, List<String> t) {
    Map<String, Double> totals = zeros(x.getFirst().columns());
    for (FittedTree fitted : forest.trees()) {
      List<Features> sampleX = RandomForest.selectColumns(pick(x, fitted.rows()), fitted.columns());
      Map<String, Double> importances =
          treeImportances(fitted.model().tree().orElseThrow(), sampleX, pick(t, fitted.rows()));
      importances.forEach(
          (feature, value) -> totals.merge(feature, value / forest.trees().size(), Double::sum));
    }
    return normalize(totals);
  }

  private static Map<String, Double> zeros(List<String> columns) {
    Map<String, Double> zeros = new LinkedHashMap<>();
    columns.forEach(column -> zeros.put(column, 0.0));
    return zeros;
  }

  private static Map<String, Double> normalize(Map<String, Double> totals) {
    double total = totals.values().stream().mapToDouble(Double::doubleValue).sum();
    if (total == 0.0) {
      return totals;
    }
    Map<String, Double> normalized = new LinkedHashMap<>();
    totals.forEach((feature, value) -> normalized.put(feature, value / total));
    return normalized;
  }
}
