package machinelearning.chapter12

import machinelearning.chapter07.Matrix

/** 第 7 章の Matrix に無い、足し算・定数倍・単位行列。
  *
  * Matrix を変えずに、この章で拡張メソッドとして足す。Java 版は static メソッドのクラス（`MatrixOperations`）にしたが、 Scala では Kotlin
  * の拡張関数と同じように `a + b`・`alpha * m` と書ける。
  */
object MatrixOps:

  extension (matrix: Matrix)
    /** 同じ大きさの行列の和。 */
    def +(other: Matrix): Matrix =
      require(
        matrix.rowCount == other.rowCount && matrix.columnCount == other.columnCount,
        s"${matrix.rowCount} 行 ${matrix.columnCount} 列の行列と " +
          s"${other.rowCount} 行 ${other.columnCount} 列の行列は足せません"
      )
      Matrix(matrix.rows.lazyZip(other.rows).map(_.lazyZip(_).map(_ + _)).toVector)

    /** すべての成分を scalar 倍した行列。 */
    def *(scalar: Double): Matrix = Matrix(matrix.rows.map(_.map(_ * scalar)))

    /** 列ごとの平均。 */
    def columnMeans: Vector[Double] =
      (0 until matrix.columnCount).toVector.map(j => matrix.column(j).sum / matrix.rowCount)

  /** size 行 size 列の単位行列。 */
  def identity(size: Int): Matrix =
    require(size >= 1, "単位行列の大きさは 1 以上にしてください")
    Matrix(
      (0 until size).toVector.map(i =>
        (0 until size).toVector.map(j => if i == j then 1.0 else 0.0)
      )
    )

/** 正則化した線形回帰のモデル。特徴量の列の順に並んだ係数と、切片を持つ。
  *
  * @param coefficients
  *   列の順に並んだ係数
  * @param intercept
  *   切片
  */
case class RegularizedModel(coefficients: Vector[Double], intercept: Double):

  /** 行ごとの予測値。行列の列数は係数の数と同じでなければならない。 */
  def predict(x: Matrix): Vector[Double] =
    require(
      x.columnCount == coefficients.size,
      s"特徴量の列数 ${x.columnCount} と係数の数 ${coefficients.size} が違います"
    )
    x.rows.map(row => intercept + row.lazyZip(coefficients).map(_ * _).sum)

  /** 係数の絶対値の合計。正則化が強いほど小さくなる。 */
  def coefficientAbsSum: Double = coefficients.map(math.abs).sum

/** リッジ回帰を閉形式（正規方程式に alpha I を足した連立方程式）で解く。 */
object Ridge:
  import MatrixOps.*

  /** 特徴量と正解から平均を引いてから (Xᵀ X + alpha I) w = Xᵀ t を解いて係数を求め、切片は平均値から求める。
    *
    * 平均を引くのは、切片に罰則をかけないため。alpha が 0 なら最小二乗法と同じ解になる。
    */
  def fit(x: Matrix, t: Vector[Double], alpha: Double): RegularizedModel =
    require(x.rowCount == t.size, "特徴量と正解の件数が違います")
    val xMeans = x.columnMeans
    val tMean = t.sum / t.size
    val centered = Matrix(x.rows.map(_.lazyZip(xMeans).map(_ - _).toVector))
    val transposed = centered.transpose
    val penalized = transposed * centered + identity(xMeans.size) * alpha
    val coefficients =
      penalized.solve(transposed * Matrix.columnVector(t.map(_ - tMean)*)).column(0)
    RegularizedModel(coefficients, tMean - xMeans.lazyZip(coefficients).map(_ * _).sum)
