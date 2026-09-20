package machinelearning.chapter10

import java.util.Random
import machinelearning.chapter02.Features
import machinelearning.chapter03.DecisionTree

/** 学習した 1 本の木と、その木が使った列・行。 */
case class FittedTree(columns: Vector[String], rows: Vector[Int], model: DecisionTree)

/** 第 3 章の決定木をブートストラップ標本と特徴量の部分集合で学習し、多数決で予測するランダムフォレスト。 */
class RandomForest(nEstimators: Int, maxFeatures: Int, maxDepth: Option[Int], seed: Long)
    extends Classifier:
  private var fittedTrees: Vector[FittedTree] = Vector.empty

  /** 学習した決定木。 */
  def trees: Vector[FittedTree] = fittedTrees

  override def fit(x: Vector[Features], t: Vector[String]): RandomForest =
    val random = Random(seed)
    val allColumns = x.head.columns
    fittedTrees = (0 until nEstimators).toVector.map { _ =>
      val rows = RandomForest.bootstrapSample(x.size, random)
      // Java 版（Collections.shuffle）と同じ手順で列を選ぶ
      val shuffled = RandomForest.shuffle(allColumns, random)
      val chosen = shuffled.take(maxFeatures).toSet
      val columns = allColumns.filter(chosen.contains)
      val sampleX = rows.map(row => RandomForest.selectColumns(x(row), columns))
      val sampleT = rows.map(t)
      val tree = maxDepth.fold(DecisionTree.unlimited())(DecisionTree.withMaxDepth)
      FittedTree(columns, rows, tree.fit(sampleX, sampleT))
    }
    this

  override def predict(x: Vector[Features]): Vector[String] =
    if fittedTrees.isEmpty then throw IllegalStateException("fit で学習してから predict を呼んでください")
    RandomForest.majorityVote(
      fittedTrees.map(tree => tree.model.predict(RandomForest.selectColumns(x, tree.columns)))
    )

object RandomForest:
  /** 深さを制限しない決定木の森。 */
  def of(nEstimators: Int, maxFeatures: Int, seed: Long): RandomForest =
    RandomForest(nEstimators, maxFeatures, None, seed)

  /** 深さの上限を指定した決定木の森。 */
  def withMaxDepth(nEstimators: Int, maxFeatures: Int, maxDepth: Int, seed: Long): RandomForest =
    require(maxDepth >= 0, "深さの上限は 0 以上にしてください")
    RandomForest(nEstimators, maxFeatures, Some(maxDepth), seed)

  /** サンプルごとに、最も多い予測を選ぶ。同数なら先に現れた予測を選ぶ。 */
  def majorityVote(votes: Vector[Vector[String]]): Vector[String] =
    votes.head.indices.toVector.map(sample => mostCommon(votes.map(_(sample))))

  /** 0 から size - 1 までの行番号を、重複を許して size 個選ぶ。 */
  def bootstrapSample(size: Int, random: Random): Vector[Int] =
    Vector.fill(size)(random.nextInt(size))

  /** 特徴量から、指定した列だけを取り出す。 */
  def selectColumns(features: Features, columns: Vector[String]): Features =
    Features(columns, columns.map(features.value))

  def selectColumns(x: Vector[Features], columns: Vector[String]): Vector[Features] =
    x.map(selectColumns(_, columns))

  /** Java の Collections.shuffle と同じ手順で並べ替える。 */
  private[chapter10] def shuffle[A](items: Vector[A], random: Random): Vector[A] =
    val array = items.toArray[Any]
    for i <- array.length - 1 to 1 by -1 do
      val j = random.nextInt(i + 1)
      val tmp = array(i)
      array(i) = array(j)
      array(j) = tmp
    array.toVector.map(_.asInstanceOf[A])

  private def mostCommon(labels: Vector[String]): String =
    val counts = labels.foldLeft(scala.collection.immutable.ListMap.empty[String, Int]) {
      (acc, label) => acc.updated(label, acc.getOrElse(label, 0) + 1)
    }
    counts.maxBy(_._2)._1
