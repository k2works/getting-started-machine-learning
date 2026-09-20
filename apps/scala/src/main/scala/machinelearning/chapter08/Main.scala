package machinelearning.chapter08

import java.nio.file.{Path, Paths}
import java.util.Locale
import machinelearning.chapter02.{Preprocessing, Row, Table}
import machinelearning.dataset.DataDir

/** クラスの重みごとの評価結果を表示し、学習済みのパイプラインを保存して読み込む。 */
object Main:
  /** 学習済みのパイプラインの保存先（apps/scala/model/ は .gitignore の対象） */
  val ModelFile: Path = Paths.get("model", "survived.model")

  private val TestSize = 0.2
  private val Seed = 0L
  private val MaxDepth = Some(5)

  /** 年齢が分からない架空の乗客 2 人（1 等客室の女性と、3 等客室の男性） */
  private val NewPassengers: Table =
    SurvivedData.features(
      Vector(
        passenger("1", "female", "", "0", "0", "50", "C"),
        passenger("3", "male", "", "0", "0", "8", "S")
      )
    )

  /** 特徴量の列の順に並べた値から、乗客 1 人分の行を作る。空文字列は欠損値。 */
  private def passenger(values: String*): Row =
    Row(SurvivedData.FeatureColumns.zip(values.toVector).toMap)

  def run(print: String => Unit): Unit = run(print, ModelFile)

  /** 保存先を指定して実行する。テストから一時ディレクトリを渡すために使う。 */
  def run(print: String => Unit, modelFile: Path): Unit =
    val rows = Table.load(Paths.get(DataDir.current(), "Survived.csv")).rows
    val t = SurvivedData.target(rows)
    val split = Preprocessing.splitTrainTest(rows, t, TestSize, Seed)
    val survived = t.count(_ == 1)
    print(s"データ件数: ${rows.size}（生存 $survived, 死亡 ${t.size - survived}）")
    print(s"訓練データ: ${split.xTrain.size} 件, テストデータ: ${split.xTest.size} 件")

    val pipelines = ClassWeight.values.toVector.map { classWeight =>
      val pipeline =
        Pipeline.build(MaxDepth, classWeight).fit(SurvivedData.features(split.xTrain), split.tTrain)
      val result = Evaluation.evaluate(pipeline, split)
      print(
        s"classWeight=${classWeight.label}: 訓練 ${format(result.trainAccuracy)}, " +
          s"テスト ${format(result.testAccuracy)}, " +
          s"生存者 ${result.survivors} 人中 ${result.foundSurvivors} 人を発見"
      )
      classWeight -> pipeline
    }.toMap

    ModelFiles.save(pipelines(ClassWeight.Balanced), modelFile)
    val predictions = ModelFiles.load(modelFile).predict(NewPassengers)
    print(s"保存したモデル: ${modelFile.getFileName}")
    print(s"架空の乗客の予測: $predictions")

  private def format(value: Double): String = String.format(Locale.ROOT, "%.3f", value)
