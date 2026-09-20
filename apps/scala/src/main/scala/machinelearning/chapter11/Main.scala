package machinelearning.chapter11

import java.nio.file.Paths
import java.util.Locale
import machinelearning.dataset.DataDir

/** Survived.csv と cinema.csv を K 分割交差検証で評価し、指標ごとの平均を表示する。 */
object Main:

  def run(print: String => Unit): Unit =
    val dataDir = DataDir.current()
    print(s"Survived（決定木、${Experiments.NSplits} 分割交差検証の平均）")
    show(print, Experiments.evaluateSurvived(Paths.get(dataDir, "Survived.csv")), 4)
    print(s"cinema（線形回帰、${Experiments.NSplits} 分割交差検証の平均）")
    show(print, Experiments.evaluateCinema(Paths.get(dataDir, "cinema.csv")), 2)

  private def show(print: String => Unit, scores: Vector[(String, Double)], decimals: Int): Unit =
    scores.foreach((name, score) =>
      print(s"  $name: ${String.format(Locale.ROOT, s"%.${decimals}f", score)}")
    )
