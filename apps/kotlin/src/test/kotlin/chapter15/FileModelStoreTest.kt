package chapter15

import chapter07.LinearModel
import chapter08.ClassWeight
import chapter08.buildPipeline
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

private fun passenger(sex: String) = Passenger(pclass = 2, sex = sex, age = null, sibSp = 0, parch = 0, fare = 20.0, embarked = null)

private fun fictionalPassengers(): Pair<AnyFrame, List<Int>> {
    val x =
        dataFrameOf(
            "Pclass" to listOf(1, 2, 3, 3, 1, 2, 3, 3),
            "Sex" to listOf("female", "female", "female", "female", "male", "male", "male", "male"),
            "Age" to listOf(25.0, 35.0, 18.0, null, 40.0, 28.0, 22.0, null),
            "SibSp" to listOf(0, 1, 0, 0, 1, 0, 0, 0),
            "Parch" to listOf(0, 0, 1, 0, 0, 0, 0, 0),
            "Fare" to listOf(60.0, 30.0, 10.0, 9.0, 55.0, 15.0, 8.0, 7.0),
            "Embarked" to listOf("C", "S", "Q", "S", "C", "S", null, "S"),
        )
    return x to listOf(1, 1, 1, 1, 0, 0, 0, 0)
}

class SalesModelStoreTest {
    private val directory: File = createTempDirectory().toFile()

    @Test
    fun `保存した線形回帰モデルを読み込んで興行収入を予測する`() {
        val store = FileModelStore(directory)
        store.saveSalesModel(
            LinearModel(intercept = 100.0, coefficients = mapOf("SNS1" to 1.0, "SNS2" to 2.0, "actor" to 0.5, "original" to 10.0)),
        )

        val model = store.loadSalesModel().getOrThrow()

        assertEquals(210.0, model.predictSales(Movie(sns1 = 10.0, sns2 = 20.0, actor = 100.0, original = 1)), absoluteTolerance = 1e-9)
    }

    @Test
    fun `モデルファイルが無ければModelNotFoundExceptionの失敗を返す`() {
        val store = FileModelStore(directory)

        val error = assertIs<ModelNotFoundException>(store.loadSalesModel().exceptionOrNull())

        assertEquals("cinema", error.model)
        assertFalse(error.message.orEmpty().contains(directory.path))
    }
}

class SurvivalModelStoreTest {
    private val directory: File = createTempDirectory().toFile()

    @Test
    fun `保存したパイプラインを読み込んで生存を判定する`() {
        val store = FileModelStore(directory)
        val (x, t) = fictionalPassengers()
        store.saveSurvivalModel(buildPipeline(maxDepth = 2, classWeight = ClassWeight.NONE).fit(x, t))

        val model = store.loadSurvivalModel().getOrThrow()

        assertTrue(model.survives(passenger("female")))
        assertFalse(model.survives(passenger("male")))
    }

    @Test
    fun `モデルファイルが無ければModelNotFoundExceptionの失敗を返す`() {
        val store = FileModelStore(directory)

        val error = assertIs<ModelNotFoundException>(store.loadSurvivalModel().exceptionOrNull())

        assertEquals("survived", error.model)
        assertFalse(error.message.orEmpty().contains(directory.path))
    }
}
