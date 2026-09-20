package machinelearning.chapter10

import machinelearning.chapter02.Features

/** ソフトマックスと勾配降下法によるロジスティック回帰。 */
class LogisticRegression(learningRate: Double = 1.0, epochs: Int = 5000) extends Classifier:
  private var classes: Vector[String] = Vector.empty
  // weights(特徴量)(品種)
  private var weights: Vector[Vector[Double]] = Vector.empty
  private var bias: Vector[Double] = Vector.empty
  private var recorded: Vector[Double] = Vector.empty

  /** 学習した品種の並び（名前の順）。 */
  def learnedClasses: Vector[String] = classes

  /** 繰り返しごとの訓練データの損失。 */
  def losses: Vector[Double] = recorded

  /** バッチ勾配降下法で重みと切片を学習する。 */
  override def fit(x: Vector[Features], t: Vector[String]): LogisticRegression =
    val rows = x.map(_.values)
    classes = t.distinct.sorted
    val targets = t.map(classes.indexOf)
    weights = Vector.fill(x.head.columns.size, classes.size)(0.0)
    bias = Vector.fill(classes.size)(0.0)
    recorded = (0 until epochs).toVector.map { _ =>
      val probabilities = rows.map(row => LogisticRegression.softmax(scores(row)))
      val loss = LogisticRegression.crossEntropy(probabilities, targets)
      // 確率 − 正解（正解の品種だけ 1 を引く）
      val errors = probabilities.zip(targets).map { (probability, target) =>
        probability.updated(target, probability(target) - 1.0)
      }
      update(rows, errors)
      loss
    }
    this

  override def predict(x: Vector[Features]): Vector[String] =
    require(classes.nonEmpty, "fit で学習してから predict を呼んでください")
    x.map(features => classes(argMax(LogisticRegression.softmax(scores(features.values)))))

  /** 特徴量ごとのスコア（切片 + 重み × 値）。 */
  private def scores(row: Vector[Double]): Vector[Double] =
    row.zip(weights).foldLeft(bias) { (acc, pair) =>
      val (value, weightsForFeature) = pair
      acc.zip(weightsForFeature).map((score, weight) => score + value * weight)
    }

  private def update(rows: Vector[Vector[Double]], errors: Vector[Vector[Double]]): Unit =
    val n = rows.size
    weights = weights.zipWithIndex.map { (weightsForFeature, f) =>
      weightsForFeature.zipWithIndex.map { (weight, k) =>
        val gradient = rows.zip(errors).map((row, error) => row(f) * error(k)).sum
        weight - learningRate * gradient / n
      }
    }
    bias = bias.zipWithIndex.map { (value, k) =>
      value - learningRate * errors.map(_(k)).sum / n
    }

  private def argMax(values: Vector[Double]): Int = values.zipWithIndex.maxBy(_._1)._2

object LogisticRegression:
  private val Epsilon = 1e-12

  /** スコアを、合計が 1 になる確率に変換する。最大値を引いてから exp を求めるので、大きな値でもあふれない。 */
  def softmax(z: Vector[Double]): Vector[Double] =
    val max = z.max
    val exps = z.map(v => math.exp(v - max))
    val total = exps.sum
    exps.map(_ / total)

  /** 交差エントロピー。正解の品種の確率の対数の平均に、マイナスを付けたもの。 */
  def crossEntropy(probabilities: Vector[Vector[Double]], targets: Vector[Int]): Double =
    -probabilities
      .zip(targets)
      .map((p, target) => math.log(p(target) + Epsilon))
      .sum / probabilities.size
