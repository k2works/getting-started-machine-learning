package machinelearning.chapter14

import java.nio.file.Paths
import java.util.Locale
import java.util.logging.{Level, Logger}
import machinelearning.dataset.DataDir

/** 卸売業者の顧客を支出額で K-means にかけ、エルボー法の SSE とクラスタごとの特徴を表示する。 */
object Main:
  private val Seed = 0L
  private val NInit = KMeans.DefaultNInit
  private val ClusterCounts = (1 to 10).toVector
  private val NClusters = 5

  // Tribuo が学習の経過を標準エラーに出すので、警告以上だけにする。
  // ロガーはガベージコレクションで設定ごと消えないように、フィールドで持ち続ける
  private val tribuoLogger = Logger.getLogger("org.tribuo")

  def run(print: String => Unit): Unit =
    tribuoLogger.setLevel(Level.WARNING)
    val x = Spending.load(Paths.get(DataDir.current(), "Wholesale.csv"))
    val columns = x.head.columns
    val points = Spending.standardize(x)

    print(s"データ件数: ${x.size}（支出額 ${columns.size} 列）")
    print(s"クラスタ数ごとの SSE（初期中心 $NInit 通りの最小値）:")
    print("クラスタ数\t自作\tTribuo（k-means++）")
    KMeans.sseByClusterCount(points, ClusterCounts, Seed, NInit).foreach { (n, sse) =>
      print(s"$n\t${format(sse, 2)}\t${format(TribuoKMeans.bestSse(points, n, Seed, NInit), 2)}")
    }

    val result = KMeans.fitWithRestarts(points, NClusters, Seed, NInit)
    print("")
    print(s"クラスタ数 $NClusters のクラスタごとの件数と平均支出額:")
    print((Vector("クラスタ", "件数") ++ columns).mkString("\t"))
    Spending.summarizeClusters(x, result.labels).foreach { summary =>
      val cells = Vector(summary.cluster.toString, summary.count.toString) ++
        columns.map(column => format(summary.means(column), 0))
      print(cells.mkString("\t"))
    }

  private def format(value: Double, digits: Int): String =
    String.format(Locale.ROOT, s"%.${digits}f", value)
