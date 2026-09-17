package chapter15

class PredictionService(
    private val store: ModelStore,
) {
    fun predictSales(movie: Movie): Result<SalesPrediction> = store.loadSalesModel().map { SalesPrediction(sales = it.predictSales(movie)) }

    fun predictSurvival(passenger: Passenger): Result<SurvivalPrediction> =
        store.loadSurvivalModel().map { SurvivalPrediction(survived = it.survives(passenger)) }

    fun health(): Map<String, Boolean> =
        mapOf(
            "cinema" to store.loadSalesModel().isSuccess,
            "survived" to store.loadSurvivalModel().isSuccess,
        )
}
