package chapter11;

import chapter02.Features;
import chapter03.DecisionTree;
import java.util.List;

/** 第 3 章の自作の決定木を、この章の Model として使うアダプター。 */
public final class DecisionTreeModel implements Model<String> {
  private final DecisionTree tree;

  public DecisionTreeModel(int maxDepth) {
    this.tree = DecisionTree.withMaxDepth(maxDepth);
  }

  @Override
  public void fit(List<Features> x, List<String> t) {
    tree.fit(x, t);
  }

  @Override
  public List<String> predict(List<Features> x) {
    return tree.predict(x);
  }
}
