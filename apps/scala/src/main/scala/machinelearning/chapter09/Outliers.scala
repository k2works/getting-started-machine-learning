package machinelearning.chapter09

import machinelearning.chapter02.{Features, TrainTestSplit}

/** 四分位範囲（IQR）による外れ値の検出。 */
object Outliers:

  /** 外れ値とみなす、四分位数から IQR の何倍離れているか */
  val DefaultK = 1.5

  private val FirstQuartile = 0.25
  private val ThirdQuartile = 0.75

  /** 分位数を求める。位置が値の間にあれば前後の値から線形補間する（pandas の quantile の既定と同じ）。 */
  def quantile(values: Vector[Double], q: Double): Double =
    val sorted = values.sorted
    val position = (sorted.size - 1) * q
    val lower = math.floor(position).toInt
    val upper = math.ceil(position).toInt
    sorted(lower) + (sorted(upper) - sorted(lower)) * (position - lower)

  /** 第 1 四分位数から IQR の k 倍より小さい値と、第 3 四分位数から k 倍より大きい値を外れ値とする。 */
  def iqrOutliers(values: Vector[Double], k: Double = DefaultK): Vector[Boolean] =
    val q1 = quantile(values, FirstQuartile)
    val q3 = quantile(values, ThirdQuartile)
    val iqr = q3 - q1
    values.map(value => value < q1 - k * iqr || value > q3 + k * iqr)

  /** 訓練データから、正解の値が外れ値の行を取り除く。テストデータはそのまま残す。 */
  def removeTargetOutliers(
      split: TrainTestSplit[Features, Double]
  ): TrainTestSplit[Features, Double] =
    val kept = split.xTrain.zip(split.tTrain).zip(iqrOutliers(split.tTrain)).collect {
      case (pair, false) => pair
    }
    TrainTestSplit(kept.map(_._1), split.xTest, kept.map(_._2), split.tTest)
