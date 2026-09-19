package chapter09;

import chapter02.Features;
import java.util.ArrayList;
import java.util.List;

/** 2 次の多項式特徴量（2 乗の項と交互作用の項）を作る。 */
public final class PolynomialFeatures {
  private PolynomialFeatures() {}

  /**
   * 2 つの列の組。left と right が同じなら 2 乗の項を表す。
   *
   * @param left 左の列
   * @param right 右の列
   */
  public record Pair(String left, String right) {
    /** 項の名前。scikit-learn の get_feature_names_out と同じ形（"RM^2"、"RM LSTAT"）にする。 */
    public String name() {
      return left.equals(right) ? left + "^2" : left + " " + right;
    }
  }

  /** 重複を許して 2 つの列を選ぶ組を、scikit-learn の PolynomialFeatures と同じ順に並べる。 */
  public static List<Pair> pairsWithReplacement(List<String> columns) {
    List<Pair> pairs = new ArrayList<>();
    for (int i = 0; i < columns.size(); i++) {
      for (int j = i; j < columns.size(); j++) {
        pairs.add(new Pair(columns.get(i), columns.get(j)));
      }
    }
    return List.copyOf(pairs);
  }

  /** 指定した列の後ろに、2 乗の項と交互作用の項を加える。 */
  public static List<Features> expand(List<Features> x, List<String> columns) {
    List<Pair> pairs = pairsWithReplacement(columns);
    List<String> names = new ArrayList<>(columns);
    pairs.forEach(pair -> names.add(pair.name()));
    return x.stream().map(f -> expandRow(f, columns, pairs, names)).toList();
  }

  private static Features expandRow(
      Features f, List<String> columns, List<Pair> pairs, List<String> names) {
    double[] values = new double[names.size()];
    for (int i = 0; i < columns.size(); i++) {
      values[i] = f.value(columns.get(i));
    }
    for (int k = 0; k < pairs.size(); k++) {
      Pair pair = pairs.get(k);
      values[columns.size() + k] = f.value(pair.left()) * f.value(pair.right());
    }
    return new Features(names, values);
  }

  /** 指定した列だけを、その順に選ぶ。 */
  public static List<Features> select(List<Features> x, List<String> columns) {
    return x.stream()
        .map(f -> new Features(columns, columns.stream().mapToDouble(f::value).toArray()))
        .toList();
  }
}
