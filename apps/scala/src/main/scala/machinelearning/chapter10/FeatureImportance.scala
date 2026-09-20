package machinelearning.chapter10

import machinelearning.chapter02.Features
import machinelearning.chapter03.{DecisionTrees, Tree}
import machinelearning.chapter03.Tree.{Leaf, Node}

/** 分割で減った不純度から、特徴量の重要度を求める。 */
object FeatureImportance:

  /** 1 本の木の重要度。合計が 1 になるように正規化する。 */
  def treeImportances(tree: Tree, x: Vector[Features], t: Vector[String]): Map[String, Double] =
    normalize(x.head.columns, impurityDecreases(tree, x, t))

  /** 森の重要度。木ごとに正規化した重要度の平均を取り、最後にもう一度正規化する。 木が使わなかった特徴量は、その木では 0 とする。
    */
  def forestImportances(
      forest: RandomForest,
      x: Vector[Features],
      t: Vector[String]
  ): Map[String, Double] =
    val columns = x.head.columns
    val totals = forest.trees.foldLeft(columns.map(_ -> 0.0).toMap) { (acc, fitted) =>
      val sampleX = fitted.rows.map(row => RandomForest.selectColumns(x(row), fitted.columns))
      val sampleT = fitted.rows.map(t)
      val importances = fitted.model.fitted
        .map(treeImportances(_, sampleX, sampleT))
        .getOrElse(Map.empty[String, Double])
      importances.foldLeft(acc) { (sums, entry) =>
        val (feature, value) = entry
        sums.updated(feature, sums.getOrElse(feature, 0.0) + value / forest.trees.size)
      }
    }
    val total = totals.values.sum
    if total == 0.0 then totals else totals.map((feature, value) => feature -> value / total)

  /** 1 回の分割で減った不純度（件数で重み付け）を、木全体から集める。 */
  private def impurityDecreases(
      tree: Tree,
      x: Vector[Features],
      t: Vector[String]
  ): Vector[(String, Double)] =
    tree match
      case Leaf(_) => Vector.empty
      case Node(split, left, right) =>
        val (leftRows, rightRows) =
          x.indices.toVector.partition(i => x(i).value(split.feature) <= split.threshold)
        val here = split.feature -> t.size * (DecisionTrees.gini(t) - split.impurity)
        here +:
          (impurityDecreases(left, leftRows.map(x), leftRows.map(t))
            ++ impurityDecreases(right, rightRows.map(x), rightRows.map(t)))

  private def normalize(
      columns: Vector[String],
      decreases: Vector[(String, Double)]
  ): Map[String, Double] =
    val total = decreases.map(_._2).sum
    columns.map { column =>
      val amount = decreases.filter(_._1 == column).map(_._2).sum
      column -> (if total == 0.0 then 0.0 else amount / total)
    }.toMap
