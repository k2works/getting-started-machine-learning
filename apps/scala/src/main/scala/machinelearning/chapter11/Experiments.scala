package machinelearning.chapter11

import java.nio.file.Path
import machinelearning.chapter02.{Features, Preprocessing, Row, Table}
import machinelearning.chapter07.{Cinema, RegressionMetrics}

/** 交差検証に渡す特徴量と正解ラベル。この章では分割の前に全体の平均値で補完する、簡略化した前処理を使う。
  *
  * @param x
  *   補完が済んだ特徴量
  * @param t
  *   正解ラベル
  */
case class Dataset[T](x: Vector[Features], t: Vector[T])

object Dataset:
  /** Survived.csv の特徴量の列 */
  val SurvivedFeatures: Vector[String] = Vector("Pclass", "Age", "male")

  private val Age = "Age"

  /** 客室クラス・年齢・男性かどうか（1 か 0）を特徴量に、Survived の文字列を正解ラベルにする。年齢の欠損値は平均値で補う。 */
  def prepareSurvived(table: Table): Dataset[String] =
    val ageMean = Preprocessing.columnMeans(table.rows, Vector(Age))(Age)
    val x = table.rows.map(row =>
      Features(
        SurvivedFeatures,
        Vector(
          number(row, "Pclass"),
          row.number(Age).getOrElse(ageMean),
          if row.text("Sex") == "male" then 1.0 else 0.0
        )
      )
    )
    Dataset(x, table.rows.map(_.text("Survived")))

  /** 第 7 章の 4 列を特徴量に、興行収入を正解にする。特徴量の欠損値は列ごとの平均値で補う。 */
  def prepareCinema(table: Table): Dataset[Double] =
    val means = Preprocessing.columnMeans(table.rows, Cinema.FeatureColumns)
    Dataset(
      Preprocessing.fillMissing(table.rows, Cinema.FeatureColumns, means),
      table.rows.map(number(_, Cinema.Target))
    )

  private def number(row: Row, column: String): Double =
    row.number(column).getOrElse(throw IllegalArgumentException(s"欠損値です: $column"))

/** Survived.csv と cinema.csv を K 分割交差検証で評価する。 */
object Experiments:

  /** 分割の数 */
  val NSplits = 5

  /** 分割の乱数のシード */
  val Seed = 0L

  private val TreeDepth = 2
  private val Survived = "1"

  /** Survived の評価指標。表示する順に並べる。 */
  val SurvivedMetrics: Vector[(String, Metric[String])] = Vector(
    "正解率" -> Metrics.accuracy,
    "適合率" -> Metrics.classificationMetric(Metrics.precision, Survived),
    "再現率" -> Metrics.classificationMetric(Metrics.recall, Survived),
    "F値" -> Metrics.classificationMetric(Metrics.f1Score, Survived)
  )

  /** cinema の評価指標。第 7 章の RMSE・MAE をそのまま関数として渡す。 */
  val CinemaMetrics: Vector[(String, Metric[Double])] = Vector(
    "RMSE" -> RegressionMetrics.rootMeanSquaredError,
    "MAE" -> RegressionMetrics.meanAbsoluteError
  )

  /** 同じ分割で、評価指標ごとに交差検証のスコアの平均を求める。 */
  def evaluate[T](
      makeModel: () => Model[T],
      data: Dataset[T],
      metrics: Vector[(String, Metric[T])]
  ): Vector[(String, Double)] =
    val folds = CrossValidation.kFold(data.x.size, NSplits, Seed)
    metrics.map { (name, metric) =>
      val scores = CrossValidation.crossValidate(makeModel, data.x, data.t, folds, metric)
      name -> scores.sum / scores.size
    }

  /** Survived.csv を深さ 2 の決定木で評価する。 */
  def evaluateSurvived(csvFile: Path): Vector[(String, Double)] =
    evaluate(
      () => DecisionTreeModel(TreeDepth),
      Dataset.prepareSurvived(Table.load(csvFile)),
      SurvivedMetrics
    )

  /** cinema.csv を線形回帰で評価する。 */
  def evaluateCinema(csvFile: Path): Vector[(String, Double)] =
    evaluate(
      () => LinearRegressionModel(),
      Dataset.prepareCinema(Table.load(csvFile)),
      CinemaMetrics
    )
