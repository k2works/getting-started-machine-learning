package machinelearning.chapter03

import java.util.Locale
import machinelearning.chapter02.Features

/** 決定木の分割。特徴量の値が境界以下なら左、境界より大きければ右へ進む。 */
case class Split(feature: String, threshold: Double, impurity: Double)

/** 決定木。葉か節のどちらかで、ほかの実装は許さない（enum で閉じる）。 */
enum Tree:
  case Leaf(label: String)
  case Node(split: Split, left: Tree, right: Tree)

/** 決定木を作り、予測し、表示する関数。 */
object DecisionTrees:
  import Tree.*

  /** ジニ不純度。ラベルが 1 種類なら 0 で、種類が多く均等なほど大きい。 */
  def gini(labels: Vector[String]): Double =
    val total = labels.size.toDouble
    1.0 - counts(labels).values.map(count => math.pow(count / total, 2)).sum

  /** 左右の不純度の重み付き平均が最も小さくなる分割。同じ不純度なら先に見つけた分割を選ぶ。 */
  def bestSplit(x: Vector[Features], t: Vector[String]): Option[Split] =
    if gini(t) == 0.0 then None
    else
      val candidates =
        for
          feature <- x.head.columns
          sorted = x.map(_.value(feature)).zip(t).sortBy(_._1)
          i <- 1 until sorted.size
          if sorted(i)._1 != sorted(i - 1)._1
          left = sorted.take(i).map(_._2)
          right = sorted.drop(i).map(_._2)
          impurity = (left.size * gini(left) + right.size * gini(right)) / sorted.size
        yield Split(feature, (sorted(i - 1)._1 + sorted(i)._1) / 2, impurity)
      // minByOption は最初の最小値を返すので、同じ不純度なら列の順で前の分割になる
      candidates.reduceOption((best, next) => if next.impurity < best.impurity then next else best)

  /** 深さの上限まで分割を繰り返して木を作る。maxDepth が None なら上限なし。 */
  def build(x: Vector[Features], t: Vector[String], maxDepth: Option[Int]): Tree =
    val split = if maxDepth.contains(0) then None else bestSplit(x, t)
    split match
      case None => Leaf(majority(t))
      case Some(s) =>
        val (left, right) = x.indices.toVector.partition(i => goesLeft(s, x(i)))
        val childDepth = maxDepth.map(_ - 1)
        Node(
          s,
          build(left.map(x), left.map(t), childDepth),
          build(right.map(x), right.map(t), childDepth)
        )

  /** 1 件の特徴量のラベルを予測する。 */
  def predictOne(tree: Tree, features: Features): String =
    tree match
      case Leaf(label) => label
      case Node(split, left, right) =>
        predictOne(if goesLeft(split, features) then left else right, features)

  /** 木を、条件ごとに字下げした文字列にする。 */
  def format(tree: Tree, indent: String = ""): String =
    tree match
      case Leaf(label) => indent + label
      case Node(split, left, right) =>
        val threshold = String.format(Locale.ROOT, "%.4f", split.threshold)
        Vector(
          s"$indent${split.feature} <= $threshold",
          format(left, indent + "  "),
          s"$indent${split.feature} > $threshold",
          format(right, indent + "  ")
        ).mkString("\n")

  /** 多数派のラベル。同数なら先に現れたラベルを選ぶ。 */
  private[chapter03] def majority(labels: Vector[String]): String =
    counts(labels).maxBy(_._2)._1

  /** ラベルごとの件数を、ラベルが先に現れた順に並べて返す。 */
  private def counts(labels: Vector[String]): Map[String, Int] =
    labels.foldLeft(scala.collection.immutable.ListMap.empty[String, Int]) { (acc, label) =>
      acc.updated(label, acc.getOrElse(label, 0) + 1)
    }

  private def goesLeft(split: Split, features: Features): Boolean =
    features.value(split.feature) <= split.threshold

/** 自作の決定木の分類器。fit で学習してから predict で予測する。 */
class DecisionTree(maxDepth: Option[Int]):
  private var tree: Option[Tree] = None

  /** 訓練データから木を作る。 */
  def fit(x: Vector[Features], t: Vector[String]): DecisionTree =
    tree = Some(DecisionTrees.build(x, t, maxDepth))
    this

  /** 学習した木。学習する前は None。 */
  def fitted: Option[Tree] = tree

  /** 特徴量ごとのラベルを予測する。 */
  def predict(x: Vector[Features]): Vector[String] =
    val fittedTree = tree.getOrElse(throw IllegalStateException("fit で学習してから predict を呼んでください"))
    x.map(DecisionTrees.predictOne(fittedTree, _))

object DecisionTree:
  /** 深さを制限しない決定木。 */
  def unlimited(): DecisionTree = DecisionTree(None)

  /** 深さの上限を指定した決定木。 */
  def withMaxDepth(maxDepth: Int): DecisionTree =
    require(maxDepth >= 0, "深さの上限は 0 以上にしてください")
    DecisionTree(Some(maxDepth))
