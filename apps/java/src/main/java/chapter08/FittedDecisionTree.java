package chapter08;

import chapter02.Features;
import java.io.Serializable;
import java.util.List;

/** 学習済みの決定木。 */
public record FittedDecisionTree(TreeNode root) implements Serializable {
  /** 特徴量ごとのラベルを予測する。 */
  public List<Integer> predict(List<Features> x) {
    return x.stream().map(features -> WeightedTrees.predictOne(root, features)).toList();
  }
}
