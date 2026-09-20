package machinelearning.chapter15

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import machinelearning.chapter02.{Features, Row, Table}
import machinelearning.chapter07.LinearModel
import machinelearning.chapter08.{FittedPipeline, ModelFiles, SurvivedData}
import scala.util.Try

/** インフラ層。学習済みモデルをディレクトリのファイルに保存し、読み込む。 */
class FileModelStore(modelDirectory: Path) extends ModelStore:
  import FileModelStore.*

  /** 線形回帰モデルをタブ区切りのテキストで保存する（第 8 章の ModelFiles と同じ方針）。 */
  def saveSalesModel(model: LinearModel): Unit =
    val _ = Files.createDirectories(modelDirectory)
    val lines = s"intercept\t${model.intercept}" +:
      model.coefficients.map((name, value) => s"$name\t$value")
    val _ = Files.writeString(salesModelFile, lines.mkString("\n"), StandardCharsets.UTF_8)

  /** 学習済みパイプラインを保存する（第 8 章の ModelFiles）。 */
  def saveSurvivalModel(pipeline: FittedPipeline): Unit =
    val _ = Files.createDirectories(modelDirectory)
    ModelFiles.save(pipeline, survivalModelFile)

  override def loadSalesModel(): Either[PredictionError, Movie => Double] =
    load(SalesModelName, salesModelFile) { file =>
      val entries = Files
        .readAllLines(file, StandardCharsets.UTF_8)
        .toArray(Array.empty[String])
        .toVector
        .map(_.split("\t", 2) match
          case Array(name, value) => name -> value.toDouble
          case other => throw IllegalArgumentException(s"読み取れない行です: ${other.mkString}"))
      val intercept = entries
        .collectFirst { case ("intercept", value) => value }
        .getOrElse(throw IllegalArgumentException("切片がありません"))
      val model = LinearModel(intercept, entries.filterNot(_._1 == "intercept"))
      movie => model.predictOne(toFeatures(model, movie))
    }

  override def loadSurvivalModel(): Either[PredictionError, Passenger => Boolean] =
    load(SurvivalModelName, survivalModelFile) { file =>
      val pipeline = ModelFiles.load(file)
      passenger =>
        pipeline.predict(Table(SurvivedData.FeatureColumns, Vector(toRow(passenger)))).head == 1
    }

  private def salesModelFile: Path = modelDirectory.resolve(s"$SalesModelName.model")

  private def survivalModelFile: Path = modelDirectory.resolve(s"$SurvivalModelName.model")

object FileModelStore:
  /** 興行収入のモデルの名前 */
  val SalesModelName = "cinema"

  /** 生存のモデルの名前 */
  val SurvivalModelName = "survived"

  /** ファイルが無ければ ModelNotFound、読み込めなければ ModelUnreadable を返す。 */
  private def load[A](model: String, file: Path)(read: Path => A): Either[PredictionError, A] =
    if !Files.exists(file) then Left(PredictionError.ModelNotFound(model))
    else Try(read(file)).toEither.left.map(_ => PredictionError.ModelUnreadable(model))

  /** 映画の特徴量を、第 7 章のモデルが期待する列の並びにそろえる。 */
  private def toFeatures(model: LinearModel, movie: Movie): Features =
    val values = Map(
      "SNS1" -> movie.sns1,
      "SNS2" -> movie.sns2,
      "actor" -> movie.actor,
      "original" -> (if movie.original then 1.0 else 0.0)
    )
    val columns = model.coefficients.map(_._1)
    Features(columns, columns.map(values))

  /** 乗客の特徴量を、第 8 章のパイプラインが受け取るセルの文字列にする。分からない値は空欄。 */
  private def toRow(passenger: Passenger): Row =
    Row(
      Map(
        "Pclass" -> passenger.pclass.code.toString,
        "Sex" -> passenger.sex.code,
        "Age" -> passenger.age.fold("")(_.toString),
        "SibSp" -> passenger.sibSp.toString,
        "Parch" -> passenger.parch.toString,
        "Fare" -> passenger.fare.toString,
        "Embarked" -> passenger.embarked.fold("")(_.code)
      )
    )
