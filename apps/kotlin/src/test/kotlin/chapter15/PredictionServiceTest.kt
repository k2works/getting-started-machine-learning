package chapter15

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PredictSalesTest {
    @Test
    fun `映画の特徴量から興行収入を予測する`() {
        val service = PredictionService(StubModelStore())

        val prediction = service.predictSales(MOVIE)

        assertEquals(Result.success(SalesPrediction(sales = 1200.0)), prediction)
    }

    @Test
    fun `モデルが無ければModelNotFoundExceptionの失敗を返す`() {
        val service = PredictionService(EmptyModelStore())

        val error = assertIs<ModelNotFoundException>(service.predictSales(MOVIE).exceptionOrNull())

        assertEquals("cinema", error.model)
        assertEquals("学習済みモデル cinema が見つかりません", error.message)
    }
}

class PredictSurvivalTest {
    @Test
    fun `生存と判定されれば生存と予測する`() {
        val service = PredictionService(StubModelStore())

        assertEquals(Result.success(SurvivalPrediction(survived = true)), service.predictSurvival(PASSENGER))
    }

    @Test
    fun `死亡と判定されれば死亡と予測する`() {
        val service = PredictionService(StubModelStore())

        val prediction = service.predictSurvival(PASSENGER.copy(pclass = 3, sex = "male", age = 30.0, fare = 8.0, embarked = "S"))

        assertEquals(Result.success(SurvivalPrediction(survived = false)), prediction)
    }
}

class HealthTest {
    @Test
    fun `モデルを読み込めればそれぞれtrueを返す`() {
        val service = PredictionService(StubModelStore())

        assertEquals(mapOf("cinema" to true, "survived" to true), service.health())
    }

    @Test
    fun `モデルが無ければそれぞれfalseを返す`() {
        val service = PredictionService(EmptyModelStore())

        assertEquals(mapOf("cinema" to false, "survived" to false), service.health())
    }
}
