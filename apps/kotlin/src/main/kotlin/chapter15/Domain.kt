package chapter15

data class Movie(
    val sns1: Double,
    val sns2: Double,
    val actor: Double,
    val original: Int,
)

data class Passenger(
    val pclass: Int,
    val sex: String,
    val age: Double?,
    val sibSp: Int,
    val parch: Int,
    val fare: Double,
    val embarked: String?,
)

data class SalesPrediction(
    val sales: Double,
)

data class SurvivalPrediction(
    val survived: Boolean,
)

/** 学習済みモデルが見つからないことを表す。メッセージにはファイルのパスを含めない */
class ModelNotFoundException(
    val model: String,
) : Exception("学習済みモデル $model が見つかりません")

/** 映画の特徴量から興行収入を予測するモデルの約束 */
interface SalesModel {
    fun predictSales(movie: Movie): Double
}

/** 乗客が生存するかを判定するモデルの約束 */
interface SurvivalModel {
    fun survives(passenger: Passenger): Boolean
}

/** 学習済みモデルの置き場の約束。読み込めなければ失敗の Result を返す */
interface ModelStore {
    fun loadSalesModel(): Result<SalesModel>

    fun loadSurvivalModel(): Result<SurvivalModel>
}
