package chapter10;

import chapter03.DecisionTree;
import java.util.List;

/** ランダムフォレストの 1 本分。使った特徴量の列、ブートストラップ標本の行番号、学習した決定木。 */
public record FittedTree(List<String> columns, List<Integer> rows, DecisionTree model) {
  public FittedTree {
    columns = List.copyOf(columns);
    rows = List.copyOf(rows);
  }
}
