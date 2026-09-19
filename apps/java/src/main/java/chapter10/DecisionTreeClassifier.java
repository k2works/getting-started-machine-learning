package chapter10;

import chapter02.Features;
import chapter03.DecisionTree;
import java.util.List;

/** 第 3 章の決定木を変更せずに Classifier に合わせるアダプター。 */
public final class DecisionTreeClassifier implements Classifier {
  private final DecisionTree tree;

  private DecisionTreeClassifier(DecisionTree tree) {
    this.tree = tree;
  }

  /** 深さを制限しない決定木。 */
  public static DecisionTreeClassifier unlimited() {
    return new DecisionTreeClassifier(DecisionTree.unlimited());
  }

  /** 深さの上限を指定した決定木。 */
  public static DecisionTreeClassifier withMaxDepth(int maxDepth) {
    return new DecisionTreeClassifier(DecisionTree.withMaxDepth(maxDepth));
  }

  @Override
  public DecisionTreeClassifier fit(List<Features> x, List<String> t) {
    tree.fit(x, t);
    return this;
  }

  @Override
  public List<String> predict(List<Features> x) {
    return tree.predict(x);
  }
}
