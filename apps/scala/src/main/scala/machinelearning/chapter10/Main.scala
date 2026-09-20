package machinelearning.chapter10

import java.nio.file.Paths
import java.util.Locale
import machinelearning.chapter02.Preprocessing
import machinelearning.dataset.DataDir

/** モデルごとの正解率と、ランダムフォレストの特徴量の重要度を表示する。 */
object Main:
  private val TestSize = 0.3
  private val Seed = 0L
  private val NEstimators = 100
  private val MaxFeatures = 2
  private val ShallowDepth = 2

  /** 名前とモデル。表示する順に並べる。 */
  def models(): Vector[(String, Classifier)] = Vector(
    s"決定木（深さ $ShallowDepth）" -> DecisionTreeClassifier.withMaxDepth(ShallowDepth),
    "ロジスティック回帰" -> LogisticRegression(),
    s"ランダムフォレスト（$NEstimators 本）" -> RandomForest.of(NEstimators, MaxFeatures, Seed),
    s"ランダムフォレスト（$NEstimators 本・深さ $ShallowDepth）" ->
      RandomForest.withMaxDepth(NEstimators, MaxFeatures, ShallowDepth, Seed)
  )

  def run(print: String => Unit): Unit =
    val split = Preprocessing.prepareIris(Paths.get(DataDir.current(), "iris.csv"), TestSize, Seed)
    print("モデル\t訓練データ\tテストデータ")
    models().foreach { (name, model) =>
      val (train, test) =
        Classifier.score(model, split.xTrain, split.tTrain, split.xTest, split.tTest)
      print(s"$name\t${format(train)}\t${format(test)}")
    }

    val forest = RandomForest.of(NEstimators, MaxFeatures, Seed).fit(split.xTrain, split.tTrain)
    print("")
    print(s"ランダムフォレスト（$NEstimators 本）の特徴量の重要度:")
    val importances = FeatureImportance.forestImportances(forest, split.xTrain, split.tTrain)
    split.xTrain.head.columns.foreach(column => print(s"$column\t${format(importances(column))}"))

  private def format(value: Double): String = String.format(Locale.ROOT, "%.4f", value)
