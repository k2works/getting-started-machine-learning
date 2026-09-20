package machinelearning.chapter12

import machinelearning.chapter02.Features
import machinelearning.chapter07.{Matrix, TribuoRegression}
import org.tribuo.regression.slm.{ElasticNetCDTrainer, SparseLinearModel}

/** Tribuo の ElasticNetCDTrainer で、ラッソ回帰とリッジ回帰を学習する。 */
object TribuoRegularization:
  import MatrixOps.columnMeans

  /** ElasticNetCDTrainer が受け付ける l1Ratio の下限の代わりに使う値。0（純粋なリッジ回帰）は受け付けない */
  private[chapter12] val MinL1Ratio = 1e-12

  private val Tolerance = 1e-10
  private val MaxIterations = 100000
  private val Seed = 0L

  private def featureNames(x: Matrix): Vector[String] =
    (0 until x.columnCount).toVector.map(j => s"x$j")

  private def toFeatures(x: Matrix): Vector[Features] =
    val names = featureNames(x)
    x.rows.map(Features(names, _))

  /** ElasticNetCDTrainer で学習し、係数と切片を取り出す。
    *
    * Tribuo は特徴量の平均を引いてから学習するので、切片は特徴量と正解の平均値から求める。
    */
  def fitElasticNet(
      x: Matrix,
      t: Vector[Double],
      alpha: Double,
      l1Ratio: Double
  ): RegularizedModel =
    val trainer = ElasticNetCDTrainer(alpha, l1Ratio, Tolerance, MaxIterations, false, Seed)
    val model = TribuoRegression.train(trainer, toFeatures(x), t).asInstanceOf[SparseLinearModel]
    val weights = model.getWeights.values.iterator.next
    val coefficients =
      featureNames(x).map(name => weights.get(model.getFeatureIDMap.get(name).getID))
    val tMean = t.sum / t.size
    RegularizedModel(coefficients, tMean - x.columnMeans.lazyZip(coefficients).map(_ * _).sum)

  /** l1Ratio を 1 にしたラッソ回帰。 */
  def fitLasso(x: Matrix, t: Vector[Double], alpha: Double): RegularizedModel =
    fitElasticNet(x, t, alpha, 1.0)

  /** 自作のリッジ回帰と同じ alpha の尺度で、ElasticNetCDTrainer にリッジ回帰を学習させる。
    *
    * ElasticNetCDTrainer は誤差の 2 乗の合計を 2n で割った値に罰則を足すので、alpha を件数 n で割って渡す。
    */
  def fitRidge(x: Matrix, t: Vector[Double], alpha: Double): RegularizedModel =
    fitElasticNet(x, t, alpha / t.size, MinL1Ratio)
