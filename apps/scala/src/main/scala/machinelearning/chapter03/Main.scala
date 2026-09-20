package machinelearning.chapter03

import java.nio.file.Paths
import java.util.Locale
import machinelearning.chapter01.KinokoTakenoko
import machinelearning.chapter02.{Features, Preprocessing, TrainTestSplit}
import machinelearning.dataset.DataDir

/** 深さごとの正解率と、深さ 2 の決定木を表示する。 */
object Main:
  private val TestSize = 0.3
  private val Seed = 0L
  private val MaxDepths = Vector(1, 2, 3, 4, 5)
  private val TreeDepthToShow = 2

  def run(print: String => Unit): Unit =
    val split = Preprocessing.prepareIris(Paths.get(DataDir.current(), "iris.csv"), TestSize, Seed)
    print("深さ\t訓練データ\tテストデータ")
    MaxDepths.foreach(depth => print(row(depth.toString, DecisionTree.withMaxDepth(depth), split)))
    print(row("制限なし", DecisionTree.unlimited(), split))

    val shallow = DecisionTree.withMaxDepth(TreeDepthToShow).fit(split.xTrain, split.tTrain)
    print("")
    print(s"深さ $TreeDepthToShow の決定木:")
    print(DecisionTrees.format(shallow.fitted.get))

  private def row(
      label: String,
      model: DecisionTree,
      split: TrainTestSplit[Features, String]
  ): String =
    val _ = model.fit(split.xTrain, split.tTrain)
    val train = KinokoTakenoko.accuracy(model.predict(split.xTrain), split.tTrain)
    val test = KinokoTakenoko.accuracy(model.predict(split.xTest), split.tTest)
    s"$label\t${format(train)}\t${format(test)}"

  private def format(value: Double): String = String.format(Locale.ROOT, "%.4f", value)
