package machinelearning.chapter13

import machinelearning.chapter07.Matrix
import org.tribuo.math.la.DenseMatrix

/** 主成分の向きに対する 1 つの列の係数。
  *
  * @param column
  *   列名
  * @param value
  *   主成分の向きの成分（符号付き）
  */
case class Loading(column: String, value: Double)

/** 学習した主成分分析のモデル。
  *
  * @param mean
  *   列ごとの平均
  * @param components
  *   主成分を 1 行に 1 つずつ、寄与率の大きい順に並べた行列
  * @param explainedVariance
  *   主成分ごとの分散（固有値）
  * @param explainedVarianceRatio
  *   主成分ごとの寄与率
  */
case class PcaModel(
    mean: Vector[Double],
    components: Matrix,
    explainedVariance: Vector[Double],
    explainedVarianceRatio: Vector[Double]
)

/** 主成分分析。 */
object Pca:

  /** 列ごとの平均。 */
  def columnMeans(x: Matrix): Vector[Double] =
    (0 until x.columnCount).toVector.map(j => x.column(j).sum / x.rowCount)

  /** 各列から平均を引く（中心化）。 */
  private def center(x: Matrix, means: Vector[Double]): Matrix =
    Matrix(x.rows.map(row => row.lazyZip(means).map(_ - _)))

  /** 列ごとの分散と、2 列ずつの共分散を並べた行列。件数から 1 を引いた数で割る。 */
  def covarianceMatrix(x: Matrix): Matrix =
    val centered = center(x, columnMeans(x))
    val divisor = (x.rowCount - 1).toDouble
    Matrix((centered.transpose * centered).rows.map(_.map(_ / divisor)))

  /** 分散共分散行列を固有値分解し、寄与率の大きい順に nComponents 個の主成分を求める。 */
  def fit(x: Matrix, nComponents: Int): PcaModel =
    val covariance =
      DenseMatrix.createDenseMatrix(covarianceMatrix(x).rows.map(_.toArray).toArray)
    val eigen = covariance
      .eigenDecomposition()
      .orElseThrow(() => IllegalArgumentException("分散共分散行列を固有値分解できません"))
    val eigenvalues = eigen.eigenvalues().toArray.toVector
    val components =
      (0 until nComponents).toVector.map(i => eigen.getEigenVector(i).toArray.toVector)
    val variances = eigenvalues.take(nComponents)
    PcaModel(
      columnMeans(x),
      normalizeSigns(Matrix(components)),
      variances,
      variances.map(_ / eigenvalues.sum)
    )

  /** 固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の要素が正になるようにそろえる。 */
  def normalizeSigns(components: Matrix): Matrix =
    Matrix(components.rows.map { row =>
      val sign = math.signum(row.maxBy(math.abs))
      row.map(_ * sign)
    })

  /** 平均を引いてから、データを主成分の向きに射影する。 */
  def transform(model: PcaModel, x: Matrix): Matrix =
    center(x, model.mean) * model.components.transpose

  /** 累積寄与率がしきい値に届くまでの主成分の数。 */
  def componentsNeeded(ratios: Vector[Double], threshold: Double): Int =
    val needed = ratios.scanLeft(0.0)(_ + _).tail.indexWhere(_ >= threshold)
    if needed < 0 then ratios.size else needed + 1

  /** 主成分の係数の絶対値が大きい順に、列名と係数を k 個返す。 */
  def topLoadings(component: Vector[Double], columns: Vector[String], k: Int): Vector[Loading] =
    columns
      .lazyZip(component)
      .map(Loading.apply)
      .sortBy(loading => -math.abs(loading.value))
      .take(k)
