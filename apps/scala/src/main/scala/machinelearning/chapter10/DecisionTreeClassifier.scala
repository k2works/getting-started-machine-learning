package machinelearning.chapter10

import machinelearning.chapter02.Features
import machinelearning.chapter03.DecisionTree

/** 第 3 章の決定木を、分類器の約束に合わせるアダプター。第 3 章のコードは変更しない。 */
class DecisionTreeClassifier(tree: DecisionTree) extends Classifier:
  override def fit(x: Vector[Features], t: Vector[String]): DecisionTreeClassifier =
    val _ = tree.fit(x, t)
    this

  override def predict(x: Vector[Features]): Vector[String] = tree.predict(x)

object DecisionTreeClassifier:
  def withMaxDepth(maxDepth: Int): DecisionTreeClassifier =
    DecisionTreeClassifier(DecisionTree.withMaxDepth(maxDepth))

  def unlimited(): DecisionTreeClassifier = DecisionTreeClassifier(DecisionTree.unlimited())
