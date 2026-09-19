package chapter08;

import chapter02.Features;
import java.util.List;

/** クラスの重みを付けられる決定木の分類器。fit で学習済みの木を返す。maxDepth が負なら深さの上限なし。 */
public record DecisionTreeClassifier(int maxDepth, ClassWeight classWeight) {
  /** 深さを制限しないことを表す値 */
  public static final int UNLIMITED = -1;

  /** 訓練データから木を作る。 */
  public FittedDecisionTree fit(List<Features> x, List<Integer> t) {
    List<Double> weights =
        switch (classWeight) {
          case NONE -> t.stream().map(label -> 1.0).toList();
          case BALANCED -> WeightedTrees.balancedWeights(t);
        };
    return new FittedDecisionTree(WeightedTrees.build(x, t, weights, maxDepth));
  }
}
