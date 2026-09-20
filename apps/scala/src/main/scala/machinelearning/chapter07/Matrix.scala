package machinelearning.chapter07

/** 変更できない行列。第 11〜13 章でも使う。
  *
  * 値は `Vector[Vector[Double]]` で持つ。Vector は変更できないので、Java 版・C# 版のように 配列を写して守る必要が無く、case class
  * の等価判定がそのまま値の比較になる。
  */
case class Matrix(rows: Vector[Vector[Double]]):
  require(rows.nonEmpty && rows.head.nonEmpty, "行列は 1 行 1 列以上でなければなりません")
  require(rows.forall(_.size == rows.head.size), "行によって列数が違います")

  /** 行数 */
  def rowCount: Int = rows.size

  /** 列数 */
  def columnCount: Int = rows.head.size

  /** i 行 j 列の値（0 始まり）。 */
  def apply(i: Int, j: Int): Double = rows(i)(j)

  /** j 列目の値。 */
  def column(j: Int): Vector[Double] = rows.map(_(j))

  /** 行列の積。左の列数と右の行数が同じでなければならない。 */
  def *(other: Matrix): Matrix =
    require(
      columnCount == other.rowCount,
      s"左の行列の列数 $columnCount と右の行列の行数 ${other.rowCount} が違います"
    )
    Matrix(rows.map { row =>
      (0 until other.columnCount).toVector.map { j =>
        row.lazyZip(other.column(j)).map(_ * _).sum
      }
    })

  /** 行と列を入れ替えた行列。 */
  def transpose: Matrix = Matrix((0 until columnCount).toVector.map(column))

  /** 正方行列 A について、A x = b を満たす列ベクトル x を部分ピボット選択つきのガウスの消去法で求める。 */
  def solve(b: Matrix): Matrix =
    require(rowCount == columnCount, s"正方行列ではありません（$rowCount 行 $columnCount 列）")
    require(b.rowCount == rowCount && b.columnCount == 1, "右辺は同じ行数の列ベクトルでなければなりません")
    val augmented = rows.lazyZip(b.column(0)).map(_ :+ _)
    Matrix.columnVector(Matrix.backSubstitute(Matrix.eliminate(augmented, 0))*)

object Matrix:
  /** 値を縦に並べた 1 列の行列（列ベクトル）を作る。 */
  def columnVector(values: Double*): Matrix = Matrix(values.toVector.map(Vector(_)))

  /** 拡大係数行列を、上三角行列になるまで前進消去する。 */
  private def eliminate(rows: Vector[Vector[Double]], pivot: Int): Vector[Vector[Double]] =
    if pivot >= rows.size then rows
    else
      val swapped = swapLargest(rows, pivot)
      val pivotRow = swapped(pivot)
      val eliminated = swapped.zipWithIndex.map { (row, i) =>
        if i <= pivot then row
        else
          val factor = row(pivot) / pivotRow(pivot)
          row.lazyZip(pivotRow).map(_ - factor * _)
      }
      eliminate(eliminated, pivot + 1)

  /** 上三角行列を、下の行から順に代入して解く。 */
  private def backSubstitute(rows: Vector[Vector[Double]]): Vector[Double] =
    val n = rows.size
    (n - 1 to 0 by -1).foldLeft(Vector.fill(n)(0.0)) { (x, i) =>
      val known = (i + 1 until n).map(j => rows(i)(j) * x(j)).sum
      x.updated(i, (rows(i)(n) - known) / rows(i)(i))
    }

  /** ピボットの列の絶対値が最も大きい行を、ピボットの行と入れ替える。 */
  private def swapLargest(rows: Vector[Vector[Double]], pivot: Int): Vector[Vector[Double]] =
    val largest = (pivot until rows.size).maxBy(i => math.abs(rows(i)(pivot)))
    rows.updated(pivot, rows(largest)).updated(largest, rows(pivot))
