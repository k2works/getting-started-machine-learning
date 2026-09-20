package machinelearning.chapter07

/** 回帰の評価指標。t は実測値、y は予測値。第 11・12 章でも使う。 */
object RegressionMetrics:

  /** 平均絶対誤差（MAE）。誤差の絶対値の平均。 */
  def meanAbsoluteError(t: Vector[Double], y: Vector[Double]): Double =
    val r = residuals(t, y)
    r.map(math.abs).sum / r.size

  /** 平均二乗誤差の平方根（RMSE）。 */
  def rootMeanSquaredError(t: Vector[Double], y: Vector[Double]): Double =
    val r = residuals(t, y)
    math.sqrt(sumOfSquares(r) / r.size)

  /** 決定係数（R²）。1 から「残差の 2 乗の合計 ÷ 平均との差の 2 乗の合計」を引いた値。 */
  def r2Score(t: Vector[Double], y: Vector[Double]): Double =
    val residual = sumOfSquares(residuals(t, y))
    val mean = t.sum / t.size
    1 - residual / sumOfSquares(t.map(_ - mean))

  /** 実測値と予測値の差。件数が違えばエラーにする。 */
  private def residuals(t: Vector[Double], y: Vector[Double]): Vector[Double] =
    require(t.size == y.size, "実測値と予測値の件数が違います")
    t.lazyZip(y).map(_ - _)

  private def sumOfSquares(values: Vector[Double]): Double = values.map(v => v * v).sum
