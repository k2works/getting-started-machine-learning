package chapter15

/** sns1 に 1000 を足すだけの興行収入のモデル */
class StubSalesModel : SalesModel {
    override fun predictSales(movie: Movie): Double = 1000.0 + movie.sns1
}

/** 女性なら生存と判定するだけのモデル */
class StubSurvivalModel : SurvivalModel {
    override fun survives(passenger: Passenger): Boolean = passenger.sex == "female"
}

/** 常にスタブのモデルを返す置き場 */
class StubModelStore : ModelStore {
    override fun loadSalesModel(): Result<SalesModel> = Result.success(StubSalesModel())

    override fun loadSurvivalModel(): Result<SurvivalModel> = Result.success(StubSurvivalModel())
}

/** モデルが 1 つも無い置き場 */
class EmptyModelStore : ModelStore {
    override fun loadSalesModel(): Result<SalesModel> = Result.failure(ModelNotFoundException("cinema"))

    override fun loadSurvivalModel(): Result<SurvivalModel> = Result.failure(ModelNotFoundException("survived"))
}

val MOVIE = Movie(sns1 = 200.0, sns2 = 500.0, actor = 3000.0, original = 1)
val PASSENGER = Passenger(pclass = 1, sex = "female", age = null, sibSp = 0, parch = 0, fare = 50.0, embarked = "C")
