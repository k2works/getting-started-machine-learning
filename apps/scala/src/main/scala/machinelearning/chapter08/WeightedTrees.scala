package machinelearning.chapter08

import machinelearning.chapter02.Features
import machinelearning.chapter03.Split
import scala.collection.immutable.ListMap

/** クラスの重みの付け方。label は表示に使う名前。 */
enum ClassWeight(val label: String):
  /** 重みを付けない（すべて 1） */
  case Unweighted extends ClassWeight("none")

  /** クラスの件数に反比例する重みを付ける */
  case Balanced extends ClassWeight("balanced")

/** 重み付きの決定木。葉か節のどちらかで、ほかの実装は許さない（enum で閉じる）。 */
enum Tree:
  case Leaf(label: Int)
  case Node(split: Split, left: Tree, right: Tree)

/** 1 件ごとの重みを使って決定木を作り、予測する関数。 */
object WeightedTrees:
  import Tree.*

  /** 重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。 */
  def weightedGini(labels: Vector[Int], weights: Vector[Double]): Double =
    val total = weights.sum
    1.0 - weightSums(labels, weights).values.map(weight => math.pow(weight / total, 2)).sum

  /** クラスの件数に反比例する重み（件数 / (クラスの数 × そのクラスの件数)）を 1 件ごとに求める。 */
  def balancedWeights(t: Vector[Int]): Vector[Double] =
    val counts = t.groupMapReduce(identity)(_ => 1)(_ + _)
    t.map(label => t.size.toDouble / (counts.size * counts(label)))

  /** クラスの重みの付け方から、1 件ごとの重みを求める。 */
  def weightsOf(t: Vector[Int], classWeight: ClassWeight): Vector[Double] =
    classWeight match
      case ClassWeight.Unweighted => t.map(_ => 1.0)
      case ClassWeight.Balanced   => balancedWeights(t)

  /** 重みの合計が最も大きいラベル。同じなら先に現れたラベルを選ぶ。 */
  private[chapter08] def weightedMajority(labels: Vector[Int], weights: Vector[Double]): Int =
    weightSums(labels, weights).maxBy(_._2)._1

  /** 左右の重み付き不純度の、重みによる平均が最も小さくなる分割。同じ不純度なら先に見つけた分割を選ぶ。 */
  private[chapter08] def bestSplit(
      x: Vector[Features],
      t: Vector[Int],
      w: Vector[Double]
  ): Option[Split] =
    if weightedGini(t, w) == 0.0 then None
    else
      val candidates =
        for
          feature <- x.head.columns
          sorted = x.map(_.value(feature)).lazyZip(t).lazyZip(w).toVector.sortBy(_._1)
          i <- 1 until sorted.size
          if sorted(i)._1 != sorted(i - 1)._1
          (leftLabels, rightLabels) = sorted.map(_._2).splitAt(i)
          (leftWeights, rightWeights) = sorted.map(_._3).splitAt(i)
          impurity = (leftWeights.sum * weightedGini(leftLabels, leftWeights)
            + rightWeights.sum * weightedGini(rightLabels, rightWeights)) / w.sum
        yield Split(feature, (sorted(i - 1)._1 + sorted(i)._1) / 2, impurity)
      candidates.reduceOption((best, next) => if next.impurity < best.impurity then next else best)

  /** 深さの上限まで分割を繰り返して木を作る。maxDepth が None なら上限なし。 */
  private[chapter08] def build(
      x: Vector[Features],
      t: Vector[Int],
      w: Vector[Double],
      maxDepth: Option[Int]
  ): Tree =
    val split = if maxDepth.contains(0) then None else bestSplit(x, t, w)
    split match
      case None => Leaf(weightedMajority(t, w))
      case Some(s) =>
        val (left, right) = x.indices.toVector.partition(i => goesLeft(s, x(i)))
        val childDepth = maxDepth.map(_ - 1)
        Node(
          s,
          build(left.map(x), left.map(t), left.map(w), childDepth),
          build(right.map(x), right.map(t), right.map(w), childDepth)
        )

  /** 1 件の特徴量のラベルを予測する。 */
  def predictOne(tree: Tree, features: Features): Int =
    tree match
      case Leaf(label) => label
      case Node(split, left, right) =>
        predictOne(if goesLeft(split, features) then left else right, features)

  private def goesLeft(split: Split, features: Features): Boolean =
    features.value(split.feature) <= split.threshold

  /** ラベルごとの重みの合計を、ラベルが先に現れた順に並べて返す。 */
  private def weightSums(labels: Vector[Int], weights: Vector[Double]): Map[Int, Double] =
    labels.zip(weights).foldLeft(ListMap.empty[Int, Double]) { case (sums, (label, weight)) =>
      sums.updated(label, sums.getOrElse(label, 0.0) + weight)
    }

/** クラスの重みを付けられる決定木の分類器。fit で学習済みの木を返す。maxDepth が None なら深さの上限なし。 */
case class DecisionTreeClassifier(maxDepth: Option[Int], classWeight: ClassWeight):
  /** 訓練データから木を作る。 */
  def fit(x: Vector[Features], t: Vector[Int]): FittedDecisionTree =
    FittedDecisionTree(WeightedTrees.build(x, t, WeightedTrees.weightsOf(t, classWeight), maxDepth))

/** 学習済みの決定木。 */
case class FittedDecisionTree(tree: Tree):
  /** 特徴量ごとのラベルを予測する。 */
  def predict(x: Vector[Features]): Vector[Int] = x.map(WeightedTrees.predictOne(tree, _))
