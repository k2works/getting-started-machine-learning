package machinelearning.chapter14

import java.nio.file.Path
import machinelearning.chapter02.{Features, Table}
import machinelearning.chapter09.Standardizer
import scala.collection.immutable.SeqMap

/** 1 つのクラスタの特徴。
  *
  * @param cluster
  *   クラスタ番号
  * @param count
  *   所属する件数
  * @param means
  *   列名ごとの平均。SeqMap なので列の順を保つ
  */
case class ClusterSummary(cluster: Int, count: Int, means: SeqMap[String, Double])

/** 卸売業者の顧客ごとの支出額（Wholesale.csv）。 */
object Spending:

  /** 区分を表す番号で、支出額ではない列 */
  val Categories: Set[String] = Set("Channel", "Region")

  /** Channel と Region を除いた支出額の列を読み込む。欠損値があれば例外を投げる。 */
  def load(csvFile: Path): Vector[Features] =
    val table = Table.load(csvFile)
    val columns = table.columns.filterNot(Categories.contains)
    table.rows.map { row =>
      Features(
        columns,
        columns.map(column =>
          row.number(column).getOrElse(throw IllegalArgumentException(s"欠損値があります: $column"))
        )
      )
    }

  /** 第 9 章の標準化（件数で割る標準偏差）で列ごとにそろえ、1 件を 1 つの点とする。 */
  def standardize(x: Vector[Features]): Vector[Vector[Double]] =
    Standardizer.fit(x).transform(x).map(_.values)

  /** クラスタごとの件数と、元の単位での列ごとの平均を、件数の多い順に並べる。 */
  def summarizeClusters(x: Vector[Features], labels: Vector[Int]): Vector[ClusterSummary] =
    labels
      .zip(x)
      .groupMap(_._1)(_._2)
      .toVector
      .sortBy(_._1)
      .map((cluster, members) => ClusterSummary(cluster, members.size, means(members)))
      .sortBy(-_.count)

  private def means(rows: Vector[Features]): SeqMap[String, Double] =
    SeqMap.from(rows.head.columns.map { column =>
      column -> rows.map(_.value(column)).sum / rows.size
    })
