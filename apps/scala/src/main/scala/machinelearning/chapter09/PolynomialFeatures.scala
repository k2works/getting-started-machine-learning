package machinelearning.chapter09

import machinelearning.chapter02.Features

/** 2 次の項。left と right が同じなら 2 乗の項を表す。
  *
  * @param left
  *   左の列
  * @param right
  *   右の列
  */
case class Term(left: String, right: String):

  /** 項の名前。scikit-learn の get_feature_names_out と同じ形（"RM^2"、"RM LSTAT"）にする。 */
  def name: String = if left == right then s"$left^2" else s"$left $right"

/** 2 次の多項式特徴量（2 乗の項と交互作用の項）を作る。 */
object PolynomialFeatures:

  /** 重複を許して 2 つの列を選ぶ組を、scikit-learn の PolynomialFeatures と同じ順に並べる。 */
  def pairsWithReplacement(columns: Vector[String]): Vector[Term] =
    columns.indices.toVector.flatMap(i => columns.drop(i).map(right => Term(columns(i), right)))

  /** 指定した列の後ろに、2 乗の項と交互作用の項を加える。 */
  def expand(x: Vector[Features], columns: Vector[String]): Vector[Features] =
    val terms = pairsWithReplacement(columns)
    val names = columns ++ terms.map(_.name)
    x.map(features =>
      Features(
        names,
        columns.map(features.value) ++ terms.map(term =>
          features.value(term.left) * features.value(term.right)
        )
      )
    )

  /** 指定した列だけを、その順に選ぶ。 */
  def select(x: Vector[Features], columns: Vector[String]): Vector[Features] =
    x.map(features => Features(columns, columns.map(features.value)))
