package chapter03;

import chapter02.Features;
import java.util.List;
import java.util.Optional;

/** 自作の決定木の分類器。fit で学習してから predict で予測する。 */
public final class DecisionTree {
  private static final int UNLIMITED = -1;

  private final int maxDepth;
  private Tree tree;

  private DecisionTree(int maxDepth) {
    this.maxDepth = maxDepth;
  }

  /** 深さを制限しない決定木。 */
  public static DecisionTree unlimited() {
    return new DecisionTree(UNLIMITED);
  }

  /** 深さの上限を指定した決定木。 */
  public static DecisionTree withMaxDepth(int maxDepth) {
    if (maxDepth < 0) {
      throw new IllegalArgumentException("深さの上限は 0 以上にしてください");
    }
    return new DecisionTree(maxDepth);
  }

  /** 訓練データから木を作る。 */
  public DecisionTree fit(List<Features> x, List<String> t) {
    tree = DecisionTrees.build(x, t, maxDepth);
    return this;
  }

  /** 学習した木。学習する前は空。 */
  public Optional<Tree> tree() {
    return Optional.ofNullable(tree);
  }

  /** 特徴量ごとのラベルを予測する。 */
  public List<String> predict(List<Features> x) {
    if (tree == null) {
      throw new IllegalStateException("fit で学習してから predict を呼んでください");
    }
    return x.stream().map(features -> DecisionTrees.predictOne(tree, features)).toList();
  }
}
