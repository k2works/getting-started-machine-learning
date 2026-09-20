package machinelearning.chapter08

import machinelearning.chapter02.{Features, Row, Table, TrainTestSplit}

/** Survived.csv の特徴量の列と正解ラベルの列。 */
object SurvivedData:
  /** モデルに渡す特徴量の列 */
  val FeatureColumns: Vector[String] =
    Vector("Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked")

  /** 正解ラベルの列（1 が生存、0 が死亡） */
  val Target = "Survived"

  /** 行を、特徴量の列だけを並べた表にする。行がほかの列を持っていても使わない。 */
  def features(rows: Vector[Row]): Table = Table(FeatureColumns, rows)

  /** 行の Survived 列を、整数の正解ラベルにする。 */
  def target(rows: Vector[Row]): Vector[Int] = rows.map(_.text(Target).toInt)

/** 前処理を順に fit・transform してから、モデルを学習する。 */
case class Pipeline(transformers: Vector[Transformer], model: DecisionTreeClassifier):

  /** 訓練データで前処理とモデルを学習する。前処理は、前の前処理で変換したデータで fit する。 */
  def fit(x: Table, t: Vector[Int]): FittedPipeline =
    val (fitted, prepared) =
      transformers.foldLeft((Vector.empty[FittedTransformer], x)) {
        case ((fitted, prepared), transformer) =>
          val fittedTransformer = transformer.fit(prepared)
          (fitted :+ fittedTransformer, fittedTransformer.transform(prepared))
      }
    FittedPipeline(fitted, model.fit(FittedPipeline.toFeatures(prepared), t))

object Pipeline:
  /** Survived.csv 用のパイプライン。年齢・港の補完とダミー変数化の後に、決定木で分類する。 */
  def build(maxDepth: Option[Int], classWeight: ClassWeight): Pipeline =
    Pipeline(
      Vector(
        GroupMedianImputer("Age", Vector("Pclass", "Sex")),
        MostFrequentImputer("Embarked"),
        DummyEncoder(Vector("Sex", "Embarked"))
      ),
      DecisionTreeClassifier(maxDepth, classWeight)
    )

/** 学習済みの前処理とモデル。予測するときは前処理の transform だけを使う。 */
case class FittedPipeline(transformers: Vector[FittedTransformer], model: FittedDecisionTree):

  /** 学習済みの前処理を順に合成して、データを変換する。 */
  def transform(x: Table): Table =
    transformers.map(_.transform).foldLeft(identity[Table])(_ andThen _)(x)

  /** 前処理をして、モデルに渡す特徴量にする。 */
  def features(x: Table): Vector[Features] = FittedPipeline.toFeatures(transform(x))

  /** 前処理をしてから、1 行ごとのラベル（1 が生存、0 が死亡）を予測する。 */
  def predict(x: Table): Vector[Int] = model.predict(features(x))

object FittedPipeline:
  /** 前処理の済んだ表を特徴量にする。欠損値が残っていれば失敗する。 */
  private[chapter08] def toFeatures(x: Table): Vector[Features] =
    x.rows.map { row =>
      Features(
        x.columns,
        x.columns.map(column =>
          row.number(column).getOrElse(throw IllegalArgumentException(s"欠損値が残っています: $column"))
        )
      )
    }

/** 訓練データとテストデータの正解率と、テストデータの生存者のうち生存と予測できた人数。 */
case class Evaluation(
    trainAccuracy: Double,
    testAccuracy: Double,
    foundSurvivors: Int,
    survivors: Int
)

object Evaluation:
  private val Survived = 1

  /** 学習済みのパイプラインを、訓練データとテストデータで評価する。 */
  def evaluate(pipeline: FittedPipeline, split: TrainTestSplit[Row, Int]): Evaluation =
    val predictions = pipeline.predict(SurvivedData.features(split.xTest))
    val labels = split.tTest
    Evaluation(
      accuracy(pipeline.predict(SurvivedData.features(split.xTrain)), split.tTrain),
      accuracy(predictions, labels),
      predictions
        .zip(labels)
        .count((prediction, label) => prediction == Survived && label == Survived),
      labels.count(_ == Survived)
    )

  private def accuracy(predictions: Vector[Int], labels: Vector[Int]): Double =
    predictions.zip(labels).count((prediction, label) => prediction == label).toDouble / labels.size
