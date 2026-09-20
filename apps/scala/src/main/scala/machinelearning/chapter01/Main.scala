package machinelearning.chapter01

import java.nio.file.Paths
import machinelearning.dataset.DataDir

/** 実データでルールによる判定の正解率を表示する。 */
object Main:
  def run(print: String => Unit): Unit =
    val people = KinokoTakenoko.loadPeople(Paths.get(DataDir.current(), "KvsT.csv"))
    val (features, labels) = KinokoTakenoko.splitFeaturesAndLabels(people)
    val predictions = features.map(KinokoTakenoko.predictByRule)
    print(s"データ件数: ${people.size}")
    print(f"ルールによる判定の正解率: ${KinokoTakenoko.accuracy(predictions, labels)}%.4f")
