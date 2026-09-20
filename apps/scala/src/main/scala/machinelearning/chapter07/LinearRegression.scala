package machinelearning.chapter07

import machinelearning.chapter02.Features

/** 学習した線形回帰のモデル。切片と、列名つきの係数を持つ。
  *
  * 係数は `Vector[(String, Double)]` なので列の順がそのまま残り、値で比べられる。 Java 版が `LinkedHashMap` を包んで守っていたことを、Scala
  * では型を選ぶだけで済ませられる。
  */
case class LinearModel(intercept: Double, coefficients: Vector[(String, Double)]):

  /** 列名で係数を読む。 */
  def coefficient(column: String): Double =
    coefficients
      .collectFirst { case (name, value) if name == column => value }
      .getOrElse(throw IllegalArgumentException(s"係数がありません: $column"))

  /** 1 行分の特徴量の予測値。係数は列名で対応させるので、列の並び順は問わない。 */
  def predictOne(features: Features): Double =
    coefficients.foldLeft(intercept)((sum, c) => sum + c._2 * features.value(c._1))

  /** 行ごとの予測値。 */
  def predict(x: Vector[Features]): Vector[Double] = x.map(predictOne)

object LinearModel:
  /** 列名と、同じ順に並んだ係数からモデルを作る。 */
  def of(intercept: Double, columns: Vector[String], coefficients: Vector[Double]): LinearModel =
    require(columns.size == coefficients.size, "列名と係数の数が違います")
    LinearModel(intercept, columns.zip(coefficients))

/** 正規方程式で線形回帰を学習する。 */
object LinearRegression:

  /** 先頭に 1 の列を足した特徴量の行列（計画行列）を作る。1 の列の係数が切片になる。 */
  private[chapter07] def designMatrix(x: Vector[Features]): Matrix =
    Matrix(x.map(1.0 +: _.values))

  /** (Xᵀ X) w = Xᵀ t を解いて、切片と係数を求める。列名は先頭の行の特徴量から取る。 */
  def fit(x: Vector[Features], t: Vector[Double]): LinearModel =
    require(x.nonEmpty, "訓練データが空です")
    require(x.size == t.size, "特徴量と実測値の件数が違います")
    val design = designMatrix(x)
    val transposed = design.transpose
    val weights = (transposed * design).solve(transposed * Matrix.columnVector(t*)).column(0)
    LinearModel.of(weights.head, x.head.columns, weights.tail)
