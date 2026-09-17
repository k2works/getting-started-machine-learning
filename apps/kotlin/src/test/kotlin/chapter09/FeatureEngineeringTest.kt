package chapter09

import chapter02.TrainTestSplit
import chapter02.countMissing
import dataset.dataDir
import org.jetbrains.kotlinx.dataframe.api.cast
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.std
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.tribuo.transform.transformations.MeanStdDevTransformation
import support.captureStdout
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.math.sqrt
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DummyCategoriesTest {
    @Test
    fun `先頭を除いたカテゴリを辞書順に返す`() {
        val crime = listOf("low", "high", "very_low", "low")

        assertEquals(listOf("low", "very_low"), dummyCategories(crime))
    }
}

class EncodeDummiesTest {
    @Test
    fun `カテゴリごとに0と1の列を作り元の列を取り除く`() {
        val df =
            dataFrameOf(
                "CRIME" to listOf("low", "high", "very_low"),
                "RM" to listOf(6.1, 5.2, 7.3),
            )

        val encoded = encodeDummies(df, "CRIME", listOf("low", "very_low"))

        assertEquals(listOf("RM", "CRIME_low", "CRIME_very_low"), encoded.columnNames())
        assertEquals(listOf(6.1, 5.2, 7.3), encoded["RM"].toList())
        assertEquals(listOf(1, 0, 0), encoded["CRIME_low"].toList())
        assertEquals(listOf(0, 0, 1), encoded["CRIME_very_low"].toList())
    }

    @Test
    fun `カテゴリに無い値はすべての列が0になる`() {
        val df = dataFrameOf("CRIME" to listOf("unknown"))

        val encoded = encodeDummies(df, "CRIME", listOf("low", "very_low"))

        assertEquals(listOf("CRIME_low", "CRIME_very_low"), encoded.columnNames())
        assertEquals(listOf(0), encoded["CRIME_low"].toList())
        assertEquals(listOf(0), encoded["CRIME_very_low"].toList())
    }
}

class StandardizerTest {
    @Test
    fun `訓練データから列ごとの平均と標準偏差を求める`() {
        val df =
            dataFrameOf(
                "RM" to listOf(1.0, 2.0, 3.0),
                "LSTAT" to listOf(10.0, 10.0, 40.0),
            )

        val standardizer = Standardizer.fit(df)

        assertEquals(2.0, standardizer.means.getValue("RM"), absoluteTolerance = 1e-12)
        assertEquals(20.0, standardizer.means.getValue("LSTAT"), absoluteTolerance = 1e-12)
        assertEquals(sqrt(2.0 / 3), standardizer.stds.getValue("RM"), absoluteTolerance = 1e-12)
        assertEquals(sqrt(200.0), standardizer.stds.getValue("LSTAT"), absoluteTolerance = 1e-12)
    }

    @Test
    fun `訓練データの平均と標準偏差で別のデータを標準化する`() {
        val standardizer = Standardizer(means = mapOf("RM" to 2.0), stds = mapOf("RM" to 0.5))
        val other = dataFrameOf("RM" to listOf(1.0, 2.0, 4.0))

        val standardized = standardizer.transform(other)

        assertEquals(listOf(-2.0, 0.0, 4.0), standardized["RM"].toList())
    }

    @Test
    fun `すべて同じ値の列は0にする`() {
        val df = dataFrameOf("CHAS" to listOf(1.0, 1.0, 1.0))

        val standardized = Standardizer.fit(df).transform(df)

        assertEquals(listOf(0.0, 0.0, 0.0), standardized["CHAS"].toList())
    }
}

private fun tribuoStandardize(
    train: List<Double>,
    values: List<Double>,
): List<Double> {
    val statistics = MeanStdDevTransformation().createStats()
    train.forEach(statistics::observeValue)
    val transformer = statistics.generateTransformer()
    return values.map(transformer::transform)
}

private fun assertValues(
    expected: List<Double>,
    actual: List<Double>,
) {
    assertEquals(expected.size, actual.size)
    expected.zip(actual).forEach { (e, a) -> assertEquals(e, a, absoluteTolerance = 1e-12) }
}

class TribuoStandardizationTest {
    private val train = listOf(5.5, 6.0, 7.5, 6.5)
    private val test = listOf(6.2, 8.0)

    @Test
    fun `TribuoのMeanStdDevTransformationは件数から1を引いて割る標準偏差を使う`() {
        val mean = train.average()
        val sampleStd = dataFrameOf("RM" to train)["RM"].cast<Double>().std(ddof = 1)

        assertValues(test.map { (it - mean) / sampleStd }, tribuoStandardize(train, test))
    }

    @Test
    fun `自作の標準化に件数から決まる係数を掛けるとTribuoの値になる`() {
        val standardized = Standardizer.fit(dataFrameOf("RM" to train)).transform(dataFrameOf("RM" to test))
        val ratio = sqrt((train.size - 1.0) / train.size)

        assertValues(tribuoStandardize(train, test), standardized["RM"].toList().map { (it as Double) * ratio })
    }
}

class PolynomialFeaturesTest {
    @Test
    fun `1列なら元の列と2乗の列を返す`() {
        val df = dataFrameOf("RM" to listOf(2.0, 3.0))

        val features = polynomialFeatures(df, listOf("RM"))

        assertEquals(listOf("RM", "RM^2"), features.columnNames())
        assertEquals(listOf(2.0, 3.0), features["RM"].toList())
        assertEquals(listOf(4.0, 9.0), features["RM^2"].toList())
    }

    @Test
    fun `2列なら2乗の列と2つの列の積の列を加える`() {
        val df =
            dataFrameOf(
                "RM" to listOf(2.0, 3.0),
                "LSTAT" to listOf(5.0, 7.0),
            )

        val features = polynomialFeatures(df, listOf("RM", "LSTAT"))

        assertEquals(listOf("RM", "LSTAT", "RM^2", "RM LSTAT", "LSTAT^2"), features.columnNames())
        assertEquals(listOf(4.0, 9.0), features["RM^2"].toList())
        assertEquals(listOf(10.0, 21.0), features["RM LSTAT"].toList())
        assertEquals(listOf(25.0, 49.0), features["LSTAT^2"].toList())
    }

    @Test
    fun `3列ならscikit_learnのPolynomialFeaturesと同じ並びで9列を作る`() {
        val df =
            dataFrameOf(
                "RM" to listOf(5.5, 6.0),
                "LSTAT" to listOf(12.0, 4.0),
                "PTRATIO" to listOf(18.0, 15.0),
            )

        val features = polynomialFeatures(df, listOf("RM", "LSTAT", "PTRATIO"))

        assertEquals(
            listOf("RM", "LSTAT", "PTRATIO", "RM^2", "RM LSTAT", "RM PTRATIO", "LSTAT^2", "LSTAT PTRATIO", "PTRATIO^2"),
            features.columnNames(),
        )
    }
}

class IqrOutliersTest {
    @Test
    fun `第3四分位数からIQRの15倍より大きい値を外れ値とする`() {
        val values = listOf(1.0, 2.0, 3.0, 4.0, 100.0)

        assertEquals(listOf(false, false, false, false, true), iqrOutliers(values))
    }

    @Test
    fun `第1四分位数からIQRの15倍より小さい値も外れ値とする`() {
        val values = listOf(-100.0, 1.0, 2.0, 3.0, 4.0)

        assertEquals(listOf(true, false, false, false, false), iqrOutliers(values))
    }
}

class QuantileTest {
    @Test
    fun `四分位数の位置が値の間にあれば前後の値から線形補間する`() {
        assertEquals(1.75, quantile(listOf(4.0, 1.0, 3.0, 2.0), 0.25), absoluteTolerance = 1e-12)
    }
}

class LoadBikeAndWeatherTest {
    private val directory: Path = createTempDirectory()

    private fun writeFile(
        name: String,
        text: String,
        charset: Charset = Charsets.UTF_8,
    ): File = File(directory.toFile(), name).apply { writeText(text, charset) }

    @Test
    fun `タブ区切りのファイルを読み込む`() {
        val tsvFile = writeFile("bike.tsv", "dteday\tweather_id\tcnt\n2030-04-01\t1\t120\n")

        val bike = loadBike(tsvFile)

        assertEquals(listOf("dteday", "weather_id", "cnt"), bike.columnNames())
        assertEquals(listOf(1), bike["weather_id"].toList())
        assertEquals(listOf(120), bike["cnt"].toList())
    }

    @Test
    fun `Shift_JISのファイルを読み込む`() {
        val csvFile = writeFile("weather.csv", "weather_id,weather\n1,晴れ\n", Charset.forName("Shift_JIS"))

        val weather = loadWeather(csvFile)

        assertEquals(listOf(1), weather["weather_id"].toList())
        assertEquals(listOf("晴れ"), weather["weather"].toList())
    }
}

class JoinWeatherTest {
    @Test
    fun `天気IDで天気の名前を結合する`() {
        val bike = dataFrameOf("weather_id" to listOf(2, 1), "cnt" to listOf(80, 120))
        val weather = dataFrameOf("weather_id" to listOf(1, 2), "weather" to listOf("晴れ", "曇り"))

        val joined = joinWeather(bike, weather)

        assertEquals(listOf("weather_id", "cnt", "weather"), joined.columnNames())
        assertEquals(listOf(80, 120), joined["cnt"].toList())
        assertEquals(listOf("曇り", "晴れ"), joined["weather"].toList())
    }

    @Test
    fun `天気の表に無い天気IDの行は残さない`() {
        val bike = dataFrameOf("weather_id" to listOf(1, 9), "cnt" to listOf(120, 30))
        val weather = dataFrameOf("weather_id" to listOf(1), "weather" to listOf("晴れ"))

        val joined = joinWeather(bike, weather)

        assertEquals(listOf(120), joined["cnt"].toList())
    }
}

class MeanCountByWeatherTest {
    @Test
    fun `天気ごとの平均利用者数を多い順に求める`() {
        val joined = dataFrameOf("weather" to listOf("雨", "晴れ", "晴れ"), "cnt" to listOf(20, 100, 140))

        assertEquals(listOf("晴れ" to 120.0, "雨" to 20.0), meanCountByWeather(joined).toList())
    }
}

class PrepareBostonTest {
    private val directory: Path = createTempDirectory()

    @Test
    fun `ダミー変数化と欠損値の補完をして特徴量と価格に分ける`() {
        val csvFile =
            File(directory.toFile(), "boston.csv").apply {
                writeText(
                    "CRIME,RM,NOX,PRICE\n" +
                        "low,6.0,,20.0\n" +
                        "high,5.0,0.5,15.0\n" +
                        "very_low,7.0,0.4,30.0\n" +
                        "low,6.5,0.6,25.0\n",
                )
            }

        val split = prepareBoston(csvFile, testSize = 0.5, seed = 0)

        val columns = listOf("RM", "NOX", "CRIME_low", "CRIME_very_low")
        assertEquals(columns, split.xTrain.columnNames())
        assertEquals(columns, split.xTest.columnNames())
        assertEquals(0, countMissing(split.xTrain).values.sum())
        assertEquals(0, countMissing(split.xTest).values.sum())
        assertEquals(2 to 2, split.tTrain.size to split.tTest.size)
    }

    @Test
    fun `整数の列に欠損値があっても平均値で補完する`() {
        val csvFile =
            File(directory.toFile(), "boston.csv").apply {
                writeText(
                    "CRIME,RAD,PRICE\n" +
                        "low,1,20.0\n" +
                        "high,,15.0\n" +
                        "very_low,4,30.0\n" +
                        "low,2,25.0\n",
                )
            }

        val split = prepareBoston(csvFile, testSize = 0.5, seed = 0)

        assertEquals(0, countMissing(split.xTrain).values.sum())
        assertEquals(0, countMissing(split.xTest).values.sum())
    }
}

private fun quadraticSplit(): TrainTestSplit<Double> {
    fun price(rm: Double): Double = 3 * rm * rm + 1

    val trainRm = listOf(1.0, 2.0, 3.0, 4.0)
    val testRm = listOf(5.0, 6.0)
    return TrainTestSplit(
        xTrain = dataFrameOf("RM" to trainRm, "LSTAT" to listOf(9.0, 7.0, 8.0, 6.0)),
        xTest = dataFrameOf("RM" to testRm, "LSTAT" to listOf(5.0, 4.0)),
        tTrain = trainRm.map(::price),
        tTest = testRm.map(::price),
    )
}

class ScoreFeatureSetTest {
    @Test
    fun `2乗の項が無いと2次式の価格を当てきれない`() {
        val (trainScore, _) = scoreFeatureSet(quadraticSplit(), listOf("RM"), listOf("RM"))

        assertTrue(trainScore < 1.0)
    }

    @Test
    fun `2乗の項を加えると2次式の価格を当てられる`() {
        val (trainScore, testScore) = scoreFeatureSet(quadraticSplit(), listOf("RM"), listOf("RM", "RM^2"))

        assertEquals(1.0, trainScore, absoluteTolerance = 1e-9)
        assertEquals(1.0, testScore, absoluteTolerance = 1e-9)
    }
}

class FitLinearRegressionTest {
    @Test
    fun `正規方程式を解いて切片と係数を求める`() {
        val rows = arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0), doubleArrayOf(1.0, 1.0), doubleArrayOf(2.0, 3.0))
        val t = rows.map { 2 + 3 * it[0] - it[1] }

        val model = fitLinearRegression(rows, t)

        assertEquals(2.0, model.intercept, absoluteTolerance = 1e-9)
        assertValues(listOf(3.0, -1.0), model.weights)
    }
}

class RemoveTargetOutliersTest {
    @Test
    fun `訓練データから価格が外れ値の行を取り除きテストデータは残す`() {
        val split =
            TrainTestSplit(
                xTrain = dataFrameOf("RM" to listOf(5.0, 6.0, 6.5, 7.0, 8.0)),
                xTest = dataFrameOf("RM" to listOf(9.0)),
                tTrain = listOf(1.0, 2.0, 3.0, 4.0, 100.0),
                tTest = listOf(500.0),
            )

        val removed = removeTargetOutliers(split)

        assertEquals(listOf(5.0, 6.0, 6.5, 7.0), removed.xTrain["RM"].toList())
        assertEquals(listOf(1.0, 2.0, 3.0, 4.0), removed.tTrain)
        assertEquals(listOf(500.0), removed.tTest)
    }
}

class FeatureEngineeringDataTest {
    private val bostonCsv = File(dataDir(), "Boston.csv")
    private val bikeTsv = File(dataDir(), "bike.tsv")
    private val weatherCsv = File(dataDir(), "weather.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(
            listOf(bostonCsv, bikeTsv, weatherCsv).all { it.exists() },
            "学習データ Boston.csv・bike.tsv・weather.csv が配置されていない（gulp data:setup）",
        )
    }

    private fun bostonSplit(): TrainTestSplit<Double> = prepareBoston(bostonCsv, testSize = 0.3, seed = 0)

    @Test
    fun `実データを70件と30件に分けて欠損値を補完する`() {
        val split = bostonSplit()

        assertEquals(70 to 30, split.xTrain.rowsCount() to split.xTest.rowsCount())
        assertEquals(0, countMissing(split.xTrain).values.sum())
        assertEquals(0, countMissing(split.xTest).values.sum())
    }

    @Test
    fun `2乗の項を加えるとテストデータの決定係数が上がる`() {
        val split = bostonSplit()

        val (_, base) = scoreFeatureSet(split, COLUMNS, COLUMNS)
        val (_, squares) = scoreFeatureSet(split, COLUMNS, COLUMNS + SQUARES)

        assertEquals(0.6457, base, absoluteTolerance = 1e-4)
        assertEquals(0.7975, squares, absoluteTolerance = 1e-4)
    }

    @Test
    fun `天気ごとの平均利用者数を求める`() {
        val joined = joinWeather(loadBike(bikeTsv), loadWeather(weatherCsv))

        val means = meanCountByWeather(joined)

        assertEquals(731, joined.rowsCount())
        assertEquals(listOf("晴れ", "曇り", "雨"), means.keys.toList())
        assertValues(listOf(4876.8, 4052.7, 1803.3), means.values.map { Math.round(it * 10) / 10.0 })
    }

    @Test
    fun `実行すると特徴量エンジニアリングの結果を表示する`() {
        val output = captureStdout { main() }

        assertEquals(
            "訓練データ: 70 件, テストデータ: 30 件\n" +
                "特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low\n" +
                "標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00\n" +
                "決定係数:\n" +
                "  元の特徴量（3 列）: 訓練 0.6293, テスト 0.6457\n" +
                "  2 乗の項を追加（6 列）: 訓練 0.7963, テスト 0.7975\n" +
                "  交互作用の項も追加（9 列）: 訓練 0.8042, テスト 0.7995\n" +
                "訓練データの PRICE の外れ値: 8 件\n" +
                "  外れ値を除いて 2 乗の項を追加: 訓練 0.7055, テスト 0.7786\n" +
                "天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3\n",
            output,
        )
    }
}
