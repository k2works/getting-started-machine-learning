package machinelearning.chapter15

/** ドメイン層。映画・乗客・予測・予測できなかった理由の型と、「モデル」と「モデルの置き場」の約束。
  * ほかのどの層も知らない。
  */

/** 客室の等級。 */
enum Pclass(val code: Int):
  case First extends Pclass(1)
  case Second extends Pclass(2)
  case Third extends Pclass(3)

/** 性別。 */
enum Sex(val code: String):
  case Male extends Sex("male")
  case Female extends Sex("female")

/** 乗船した港。 */
enum Embarked(val code: String):
  case Cherbourg extends Embarked("C")
  case Queenstown extends Embarked("Q")
  case Southampton extends Embarked("S")

/** 興行収入を予測する映画の特徴量。 */
case class Movie(sns1: Double, sns2: Double, actor: Double, original: Boolean)

/** 生存を予測する乗客。年齢と乗船港は分からないことがある。 */
case class Passenger(
    pclass: Pclass,
    sex: Sex,
    age: Option[Double],
    sibSp: Int,
    parch: Int,
    fare: Double,
    embarked: Option[Embarked]
)

/** 予測した興行収入。 */
case class SalesPrediction(sales: Double)

/** 生存するかどうかの予測。 */
case class SurvivalPrediction(survived: Boolean)

/** 予測できなかった理由。Scala 3 の enum で、F# の判別共用体と同じ形にする。 */
enum PredictionError(val model: String):
  /** 学習済みモデルが見つからない。持つのはモデルの名前だけで、ファイルのパスは持たない。 */
  case ModelNotFound(override val model: String) extends PredictionError(model)

  /** 学習済みモデルのファイルはあるが、読み込めない（壊れている・形が違う）。 */
  case ModelUnreadable(override val model: String) extends PredictionError(model)

  /** 人に見せる説明。 */
  def describe: String = this match
    case ModelNotFound(name)   => s"学習済みモデル $name が見つかりません"
    case ModelUnreadable(name) => s"学習済みモデル $name を読み込めません"

/** 学習済みモデルの置き場。読み込めなければ Left を返す（F# 版の Result と同じ形）。 */
trait ModelStore:
  def loadSalesModel(): Either[PredictionError, Movie => Double]

  def loadSurvivalModel(): Either[PredictionError, Passenger => Boolean]
