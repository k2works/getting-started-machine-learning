package machinelearning.chapter13

import java.nio.file.Paths
import java.util.Locale
import machinelearning.dataset.DataDir

/** ボストンの住宅価格の 15 列を主成分分析し、寄与率と主成分の意味を表示する。 */
object Main:
  private val Threshold = 0.8
  private val TopK = 3
  private val ComponentsToExplain = 2

  def run(print: String => Unit): Unit =
    val features = BostonPca.load(Paths.get(DataDir.current(), "Boston.csv"))
    val columns = features.head.columns
    val x = BostonPca.toMatrix(features)
    val model = Pca.fit(x, columns.size)
    val ratios = model.explainedVarianceRatio
    val needed = Pca.componentsNeeded(ratios, Threshold)
    val cumulative = ratios.take(needed).sum

    print(s"データ件数: ${x.rowCount}, 列数: ${columns.size}")
    print(
      "寄与率: " + ratios
        .take(needed)
        .zipWithIndex
        .map((ratio, i) => s"PC${i + 1} ${format(ratio, 4)}")
        .mkString(", ")
    )
    print(s"累積寄与率が $Threshold に届く主成分の数: $needed（累積寄与率 ${format(cumulative, 4)}）")
    model.components.rows.take(ComponentsToExplain).zipWithIndex.foreach { (component, i) =>
      val loadings = Pca
        .topLoadings(component, columns, TopK)
        .map(loading => s"${loading.column} ${format(loading.value, 3)}")
        .mkString(", ")
      print(s"第 ${i + 1} 主成分で影響の大きい列: $loadings")
    }

  private def format(value: Double, digits: Int): String =
    String.format(Locale.ROOT, s"%.${digits}f", value)
