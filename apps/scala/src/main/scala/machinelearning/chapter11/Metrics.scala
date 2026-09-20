package machinelearning.chapter11

/** 2 値分類の混同行列。正例（見つけたいほう）を決めて、予測の当たり外れを 4 つに分けて数える。
  *
  * @param tp
  *   実際は正例で、正例と予測した件数（真陽性）
  * @param fp
  *   実際は負例で、正例と予測した件数（偽陽性）
  * @param fn
  *   実際は正例で、負例と予測した件数（偽陰性）
  * @param tn
  *   実際は負例で、負例と予測した件数（真陰性）
  */
case class ConfusionMatrix(tp: Int, fp: Int, fn: Int, tn: Int)

object ConfusionMatrix:
  /** 正解と予測を 1 件ずつ比べて数える。positive と等しいラベルを正例、それ以外を負例とする。 */
  def of[A](actual: Vector[A], predicted: Vector[A], positive: A): ConfusionMatrix =
    Metrics.requireSameSize(actual, predicted)
    actual.lazyZip(predicted).foldLeft(ConfusionMatrix(0, 0, 0, 0)) { (cm, pair) =>
      (pair._1 == positive, pair._2 == positive) match
        case (true, true)   => cm.copy(tp = cm.tp + 1)
        case (false, true)  => cm.copy(fp = cm.fp + 1)
        case (true, false)  => cm.copy(fn = cm.fn + 1)
        case (false, false) => cm.copy(tn = cm.tn + 1)
    }

/** 分類と回帰の評価指標。回帰の RMSE・MAE は第 7 章の [[machinelearning.chapter07.RegressionMetrics]] を使う。 */
object Metrics:

  /** 正解と予測の件数が同じでなければ例外を投げる。短いほうに合わせて黙って切り詰めない。 */
  private[chapter11] def requireSameSize(actual: Vector[?], predicted: Vector[?]): Unit =
    require(
      actual.size == predicted.size,
      s"正解と予測の件数が違います（正解 ${actual.size} 件、予測 ${predicted.size} 件）"
    )

  private def ratio(numerator: Double, denominator: Double): Double =
    if denominator == 0 then 0.0 else numerator / denominator

  /** 適合率。正例と予測したうち、本当に正例だった割合。 */
  def precision(cm: ConfusionMatrix): Double = ratio(cm.tp, cm.tp + cm.fp)

  /** 再現率。本当の正例のうち、正例と予測できた割合。 */
  def recall(cm: ConfusionMatrix): Double = ratio(cm.tp, cm.tp + cm.fn)

  /** F 値。適合率と再現率の調和平均。 */
  def f1Score(cm: ConfusionMatrix): Double =
    val p = precision(cm)
    val r = recall(cm)
    ratio(2 * p * r, p + r)

  /** 平均二乗誤差（MSE）。誤差の 2 乗の平均。 */
  def meanSquaredError(actual: Vector[Double], predicted: Vector[Double]): Double =
    requireSameSize(actual, predicted)
    actual.lazyZip(predicted).map((t, y) => (y - t) * (y - t)).sum / actual.size

  /** 正解率。正解と予測が一致した割合。 */
  def accuracy[A](actual: Vector[A], predicted: Vector[A]): Double =
    requireSameSize(actual, predicted)
    actual.lazyZip(predicted).count(_ == _).toDouble / actual.size

  /** 混同行列から求める指標を、正例を決めて、正解と予測から求める評価関数に変える。 */
  def classificationMetric[A](score: ConfusionMatrix => Double, positive: A): Metric[A] =
    (actual, predicted) => score(ConfusionMatrix.of(actual, predicted, positive))
