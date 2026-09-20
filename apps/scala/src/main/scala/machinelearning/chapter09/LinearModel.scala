package machinelearning.chapter09

import org.tribuo.math.la.{DenseMatrix, DenseVector}
import scala.jdk.OptionConverters.*

/** 訓練データとテストデータの決定係数。
  *
  * @param train
  *   訓練データの決定係数
  * @param test
  *   テストデータの決定係数
  */
case class Scores(train: Double, test: Double)

/** 線形回帰のモデル。正規方程式を Tribuo の行列（コレスキー分解）で解いて求める。
  *
  * @param intercept
  *   切片
  * @param weights
  *   特徴量の列ごとの係数
  */
case class LinearModel(intercept: Double, weights: Vector[Double]):

  /** 1 行の特徴量から予測する。 */
  def predict(row: Vector[Double]): Double = intercept + weights.zip(row).map(_ * _).sum

  /** 行ごとに予測する。 */
  def predict(rows: Vector[Vector[Double]]): Vector[Double] = rows.map(predict)

object LinearModel:

  /** 先頭に 1 の列を加えた計画行列 X で、正規方程式 XᵀX β = Xᵀt を解く。 */
  def fit(rows: Vector[Vector[Double]], t: Vector[Double]): LinearModel =
    val design = rows.map(row => (1.0 +: row).toArray).toArray
    val x = DenseMatrix.createDenseMatrix(design)
    val transposed = x.transpose()
    val cholesky = transposed
      .matrixMultiply(x)
      .choleskyFactorization()
      .toScala
      .getOrElse(throw IllegalArgumentException("特徴量の列が互いに独立でないため、正規方程式を解けません"))
    val target = DenseVector.createDenseVector(t.toArray)
    val beta = cholesky.solve(transposed.leftMultiply(target)).toArray.toVector
    LinearModel(beta.head, beta.tail)

  /** 決定係数。1 から、残差の 2 乗和を平均との差の 2 乗和で割った値を引く。 */
  def rSquared(actual: Vector[Double], predicted: Vector[Double]): Double =
    val mean = actual.sum / actual.size
    val residual = actual.zip(predicted).map((a, p) => (a - p) * (a - p)).sum
    val total = actual.map(a => (a - mean) * (a - mean)).sum
    1 - residual / total
