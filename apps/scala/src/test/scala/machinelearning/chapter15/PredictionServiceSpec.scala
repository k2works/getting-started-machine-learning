package machinelearning.chapter15

import org.scalatest.funsuite.AnyFunSuite

/** テスト用のモデルの置き場。 */
object Stubs:
  def store(sales: Movie => Double, survival: Passenger => Boolean): ModelStore = new ModelStore:
    def loadSalesModel(): Either[PredictionError, Movie => Double] = Right(sales)
    def loadSurvivalModel(): Either[PredictionError, Passenger => Boolean] = Right(survival)

  /** どのモデルも見つからない置き場。 */
  def empty: ModelStore = new ModelStore:
    def loadSalesModel(): Either[PredictionError, Movie => Double] =
      Left(PredictionError.ModelNotFound("cinema"))
    def loadSurvivalModel(): Either[PredictionError, Passenger => Boolean] =
      Left(PredictionError.ModelNotFound("survived"))

  /** ファイルはあるが読み込めない置き場。 */
  def unreadable: ModelStore = new ModelStore:
    def loadSalesModel(): Either[PredictionError, Movie => Double] =
      Left(PredictionError.ModelUnreadable("cinema"))
    def loadSurvivalModel(): Either[PredictionError, Passenger => Boolean] =
      Left(PredictionError.ModelUnreadable("survived"))

  val movie: Movie = Movie(100.0, 200.0, 300.0, true)
  val passenger: Passenger =
    Passenger(Pclass.First, Sex.Female, Some(30.0), 0, 0, 50.0, Some(Embarked.Southampton))

class PredictionServiceSpec extends AnyFunSuite:
  test("映画の特徴量から興行収入を予測する") {
    val service = PredictionService(Stubs.store(_ => 1234.5, _ => true))

    assert(service.predictSales(Stubs.movie) === Right(SalesPrediction(1234.5)))
  }

  test("生存と判定されれば生存と予測する") {
    val service = PredictionService(Stubs.store(_ => 0.0, _ => true))

    assert(service.predictSurvival(Stubs.passenger) === Right(SurvivalPrediction(true)))
  }

  test("死亡と判定されれば死亡と予測する") {
    val service = PredictionService(Stubs.store(_ => 0.0, _ => false))

    assert(service.predictSurvival(Stubs.passenger) === Right(SurvivalPrediction(false)))
  }

  test("モデルが無ければ ModelNotFound を返す") {
    val error = PredictionService(Stubs.empty).predictSales(Stubs.movie).swap.toOption.get

    assert(error === PredictionError.ModelNotFound("cinema"))
    assert(error.describe === "学習済みモデル cinema が見つかりません")
  }

  test("モデルを読み込めなければ ModelUnreadable を返す") {
    val error =
      PredictionService(Stubs.unreadable).predictSurvival(Stubs.passenger).swap.toOption.get

    assert(error.describe === "学習済みモデル survived を読み込めません")
  }

  test("モデルを読み込めればどちらも true を返す") {
    assert(PredictionService(Stubs.store(_ => 0.0, _ => true)).health === Health(true, true))
  }

  test("モデルが無ければどちらも false を返す") {
    assert(PredictionService(Stubs.empty).health === Health(false, false))
  }

class RequestValidationSpec extends AnyFunSuite:
  private val movieJson = """{"sns1": 200, "sns2": 500, "actor": 3000, "original": 1}"""
  private val passengerJson =
    """{"pclass": 1, "sex": "female", "age": null, "sib_sp": 0, "parch": 0, "fare": 50.0, "embarked": "C"}"""

  test("正しい JSON を映画の特徴量にする") {
    assert(RequestValidation.parseMovie(movieJson) === Right(Movie(200, 500, 3000, true)))
  }

  test("年齢と乗船港は省略できる") {
    val body = """{"pclass": 3, "sex": "male", "sib_sp": 0, "parch": 0, "fare": 8.0}"""

    val passenger = RequestValidation.parsePassenger(body).toOption.get

    assert(passenger.age === None)
    assert(passenger.embarked === None)
  }

  test("null の年齢は省略と同じに扱う") {
    val passenger = RequestValidation.parsePassenger(passengerJson).toOption.get

    assert(passenger.age === None)
    assert(passenger.embarked === Some(Embarked.Cherbourg))
  }

  test("不正な項目の理由を返す") {
    val cases = Vector(
      """{"sns1": -1, "sns2": 500, "actor": 3000, "original": 1}""" -> "sns1 は 0 以上にしてください",
      """{"sns1": "多い", "sns2": 500, "actor": 3000, "original": 1}""" -> "sns1 は数値にしてください",
      """{"sns1": 200, "sns2": 500, "actor": 3000, "original": 2}""" -> "original は 0、1 のどれかにしてください",
      """{"sns2": 500, "actor": 3000, "original": 1}""" -> "sns1 を指定してください"
    )

    cases.foreach { (body, expected) =>
      assert(RequestValidation.parseMovie(body).swap.toOption.get.contains(expected), body)
    }
  }

  test("不正な項目が複数あれば理由をすべて集める") {
    val body = """{"sns1": -1, "sns2": -2, "actor": 3000, "original": 1}"""

    assert(
      RequestValidation.parseMovie(body).swap.toOption.get
        === Vector("sns1 は 0 以上にしてください", "sns2 は 0 以上にしてください")
    )
  }

  test("JSON として読めなければ理由を返す") {
    assert(RequestValidation.parseMovie("{").swap.toOption.get === Vector("JSON の形式が正しくありません"))
    assert(
      RequestValidation.parseMovie("[1, 2]").swap.toOption.get === Vector("JSON のオブジェクトにしてください")
    )
  }
