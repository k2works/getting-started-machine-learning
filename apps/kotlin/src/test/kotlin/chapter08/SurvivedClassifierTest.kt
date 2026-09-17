package chapter08

import chapter02.TrainTestSplit
import chapter02.splitTrainTest
import chapter03.DecisionTree
import chapter03.gini
import chapter03.predictWithTribuo
import chapter03.trainTribuoTree
import dataset.dataDir
import org.jetbrains.kotlinx.dataframe.AnyFrame
import org.jetbrains.kotlinx.dataframe.api.asColumnGroup
import org.jetbrains.kotlinx.dataframe.api.dataFrameOf
import org.jetbrains.kotlinx.dataframe.api.pivotMatches
import org.jetbrains.kotlinx.dataframe.api.toMap
import org.junit.jupiter.api.Assumptions.assumeTrue
import support.captureStdout
import java.io.File
import java.io.InvalidClassException
import java.io.ObjectOutputStream
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val HEADER = "\uFEFFPassengerId,Survived,Pclass,Sex,Age,SibSp,Parch,Ticket,Fare,Cabin,Embarked\n"

class LoadSurvivedTest {
    private val directory: Path = createTempDirectory()

    private fun writeCsv(rows: String): File = File(directory.toFile(), "survived.csv").apply { writeText(HEADER + rows) }

    @Test
    fun `BOM付きCSVを読み込み空欄を欠損値にする`() {
        val df = loadSurvived(writeCsv("1,0,3,male,,0,0,X-1,8.5,,S\n"))

        assertEquals("male", df["Sex"][0])
        assertNull(df["Age"][0])
        assertNull(df["Cabin"][0])
    }

    @Test
    fun `1文字の値の列も文字列として読み込む`() {
        val df = loadSurvived(writeCsv("1,0,3,male,,0,0,X-1,8.5,,S\n"))

        assertEquals("S", df["Embarked"][0])
    }
}

class GroupMedianImputerTest {
    @Test
    fun `同じグループの中央値で欠損値を補完する`() {
        val df =
            dataFrameOf(
                "Pclass" to listOf(1, 1, 1, 1),
                "Sex" to listOf("female", "female", "female", "female"),
                "Age" to listOf(20.0, 30.0, 70.0, null),
            )
        val imputer = GroupMedianImputer(column = "Age", by = listOf("Pclass", "Sex"))

        val filled = imputer.fit(df).transform(df)

        assertEquals(listOf(20.0, 30.0, 70.0, 30.0), filled["Age"].toList())
    }

    @Test
    fun `グループごとに異なる中央値で補完する`() {
        val df =
            dataFrameOf(
                "Pclass" to listOf(1, 1, 1, 3, 3, 3),
                "Sex" to listOf("female", "female", "female", "male", "male", "male"),
                "Age" to listOf(40.0, 50.0, null, 10.0, 20.0, null),
            )
        val imputer = GroupMedianImputer(column = "Age", by = listOf("Pclass", "Sex"))

        val filled = imputer.fit(df).transform(df)

        assertEquals(listOf(40.0, 50.0, 45.0, 10.0, 20.0, 15.0), filled["Age"].toList())
    }

    @Test
    fun `訓練データで求めた中央値を別のデータの補完に使う`() {
        val train = dataFrameOf("Pclass" to listOf(2, 2), "Sex" to listOf("male", "male"), "Age" to listOf(30.0, 34.0))
        val other = dataFrameOf("Pclass" to listOf(2), "Sex" to listOf("male"), "Age" to listOf(null))
        val imputer = GroupMedianImputer(column = "Age", by = listOf("Pclass", "Sex"))

        val filled = imputer.fit(train).transform(other)

        assertEquals(listOf(32.0), filled["Age"].toList())
    }

    @Test
    fun `訓練データに無いグループは全体の中央値で補完する`() {
        val train =
            dataFrameOf(
                "Pclass" to listOf(1, 1, 3),
                "Sex" to listOf("female", "female", "male"),
                "Age" to listOf(30.0, 40.0, 20.0),
            )
        val other = dataFrameOf("Pclass" to listOf(2), "Sex" to listOf("female"), "Age" to listOf(null))
        val imputer = GroupMedianImputer(column = "Age", by = listOf("Pclass", "Sex"))

        val filled = imputer.fit(train).transform(other)

        assertEquals(listOf(30.0), filled["Age"].toList())
    }

    @Test
    fun `元のデータフレームは変更しない`() {
        val df = dataFrameOf("Pclass" to listOf(1, 1), "Sex" to listOf("male", "male"), "Age" to listOf(30.0, null))
        val imputer = GroupMedianImputer(column = "Age", by = listOf("Pclass", "Sex"))

        imputer.fit(df).transform(df)

        assertNull(df["Age"][1])
    }

    @Test
    fun `年齢がすべて欠けたグループは全体の中央値で補完する`() {
        val df =
            dataFrameOf(
                "Pclass" to listOf(1, 1, 2),
                "Sex" to listOf("female", "female", "female"),
                "Age" to listOf(30.0, 40.0, null),
            )
        val imputer = GroupMedianImputer(column = "Age", by = listOf("Pclass", "Sex"))

        val filled = imputer.fit(df).transform(df)

        assertEquals(listOf(30.0, 40.0, 35.0), filled["Age"].toList())
    }
}

class MostFrequentImputerTest {
    @Test
    fun `訓練データで最も多い値で欠損値を補完する`() {
        val train = dataFrameOf("Embarked" to listOf("S", "C", "S", null))
        val other = dataFrameOf("Embarked" to listOf(null, "Q"))
        val imputer = MostFrequentImputer(column = "Embarked")

        val filled = imputer.fit(train).transform(other)

        assertEquals(listOf("S", "Q"), filled["Embarked"].toList())
    }

    @Test
    fun `欠損値だけの列も補完する`() {
        val train = dataFrameOf("Embarked" to listOf("S", "C", "S"))
        val other = dataFrameOf("Embarked" to listOf(null))
        val imputer = MostFrequentImputer(column = "Embarked")

        val filled = imputer.fit(train).transform(other)

        assertEquals(listOf("S"), filled["Embarked"].toList())
    }
}

class PivotMatchesLearningTest {
    @Test
    fun `pivotMatchesはデータに含まれるカテゴリの列しか作らない`() {
        val train = dataFrameOf("PassengerId" to listOf(1, 2, 3), "Embarked" to listOf("C", "Q", "S"))
        val other = dataFrameOf("PassengerId" to listOf(4), "Embarked" to listOf("S"))

        assertEquals(listOf("C", "Q", "S"), train.pivotMatches("Embarked")["Embarked"].asColumnGroup().columnNames())
        assertEquals(listOf("S"), other.pivotMatches("Embarked")["Embarked"].asColumnGroup().columnNames())
    }
}

class DummyEncoderTest {
    @Test
    fun `2値のカテゴリを最初のカテゴリを除いた0と1の列にする`() {
        val df = dataFrameOf("Pclass" to listOf(1, 3, 2), "Sex" to listOf("female", "male", "male"))
        val encoder = DummyEncoder(columns = listOf("Sex"))

        val encoded = encoder.fit(df).transform(df)

        assertEquals(mapOf("Pclass" to listOf(1, 3, 2), "Sex_male" to listOf(0, 1, 1)), encoded.toMap())
    }

    @Test
    fun `別のデータにも訓練データと同じ列を作る`() {
        val train = dataFrameOf("Embarked" to listOf("C", "Q", "S"))
        val other = dataFrameOf("Embarked" to listOf("S", "S"))
        val encoder = DummyEncoder(columns = listOf("Embarked"))

        val encoded = encoder.fit(train).transform(other)

        assertEquals(mapOf("Embarked_Q" to listOf(0, 0), "Embarked_S" to listOf(1, 1)), encoded.toMap())
    }
}

class WeightedGiniTest {
    @Test
    fun `重みがすべて1なら第3章のジニ不純度と同じ値になる`() {
        val labels = listOf(0, 1, 1)

        assertEquals(gini(labels.map { it.toString() }), weightedGini(labels, weights = listOf(1.0, 1.0, 1.0)))
    }

    @Test
    fun `重みの大きいラベルほど多いものとして不純度を計算する`() {
        val impurity = weightedGini(listOf(0, 1), weights = listOf(1.0, 3.0))

        assertEquals(0.375, impurity, absoluteTolerance = 1e-12)
    }
}

class BalancedWeightsTest {
    @Test
    fun `少ないクラスほど1件の重みを大きくしクラスごとの重みの合計をそろえる`() {
        val weights = balancedWeights(listOf(0, 0, 0, 1))

        assertEquals(listOf(4.0 / 6, 4.0 / 6, 4.0 / 6, 2.0), weights)
        assertEquals(weights.take(3).sum(), weights[3], absoluteTolerance = 1e-12)
    }
}

class DecisionTreeClassifierTest {
    private val x =
        dataFrameOf(
            "Fare" to listOf(8.0, 9.0, 13.0, 20.0, 60.0, 80.0),
            "Age" to listOf(30.0, 22.0, 18.0, 45.0, 25.0, 33.0),
        )
    private val t = listOf(0, 0, 1, 0, 1, 1)

    @Test
    fun `重み付けなしなら第3章の決定木と同じ予測をする`() {
        for (maxDepth in listOf(1, 2, null)) {
            val expected = DecisionTree(maxDepth).fit(x, t.map { it.toString() }).predict(x)

            val predictions = DecisionTreeClassifier(maxDepth, ClassWeight.NONE).fit(x, t).predict(x)

            assertEquals(expected, predictions.map { it.toString() }, "深さ $maxDepth")
        }
    }

    @Test
    fun `balancedにすると少ないクラスが混ざった葉でも少ないクラスを予測する`() {
        val fare = dataFrameOf("Fare" to listOf(1.0, 1.0, 1.0, 1.0, 2.0, 2.0, 2.0))
        val survived = listOf(0, 0, 0, 0, 0, 0, 1)
        val newX = dataFrameOf("Fare" to listOf(1.0, 2.0))

        val none = DecisionTreeClassifier(maxDepth = 1, classWeight = ClassWeight.NONE).fit(fare, survived)
        val balanced = DecisionTreeClassifier(maxDepth = 1, classWeight = ClassWeight.BALANCED).fit(fare, survived)

        assertEquals(listOf(0, 0), none.predict(newX))
        assertEquals(listOf(0, 1), balanced.predict(newX))
    }
}

class SplitFeaturesAndTargetTest {
    @Test
    fun `特徴量の列とSurvived列に分ける`() {
        val df =
            dataFrameOf(
                "PassengerId" to listOf(1),
                "Survived" to listOf(1),
                "Pclass" to listOf(2),
                "Sex" to listOf("female"),
                "Age" to listOf(28.0),
                "SibSp" to listOf(0),
                "Parch" to listOf(1),
                "Ticket" to listOf("X-2"),
                "Fare" to listOf(15.0),
                "Cabin" to listOf(null),
                "Embarked" to listOf("C"),
            )

        val (x, t) = splitFeaturesAndTarget(df)

        assertEquals(listOf("Pclass", "Sex", "Age", "SibSp", "Parch", "Fare", "Embarked"), x.columnNames())
        assertEquals(listOf(1), t)
    }
}

private fun passengers(vararg rows: List<Any?>): AnyFrame =
    dataFrameOf(
        *FEATURES
            .mapIndexed { i, name ->
                name to
                    rows.map {
                        it[i]
                    }
            }.toTypedArray(),
    )

private fun trainingData(): Pair<AnyFrame, List<Int>> {
    val x =
        passengers(
            listOf(1, "female", 30.0, 0, 0, 80.0, "C"),
            listOf(2, "female", null, 1, 0, 20.0, "S"),
            listOf(3, "female", 22.0, 0, 1, 9.0, null),
            listOf(3, "female", 18.0, 0, 0, 8.0, "Q"),
            listOf(1, "male", 45.0, 0, 0, 60.0, "S"),
            listOf(2, "male", null, 0, 0, 13.0, "S"),
            listOf(3, "male", 25.0, 1, 0, 7.0, "S"),
            listOf(3, "male", 33.0, 0, 0, 8.0, null),
        )
    val t = listOf(1, 1, 1, 1, 0, 0, 0, 0)
    return x to t
}

private fun newPassengers(): AnyFrame =
    passengers(
        listOf(2, "female", null, 0, 0, 12.0, null),
        listOf(1, "male", null, 1, 1, 70.0, "C"),
    )

class BuildPipelineTest {
    @Test
    fun `欠損値を含むデータで学習して予測できる`() {
        val (x, t) = trainingData()
        val pipeline = buildPipeline(maxDepth = 3, classWeight = ClassWeight.NONE)

        val fitted = pipeline.fit(x, t)

        assertEquals(listOf(1, 0), fitted.predict(newPassengers()))
    }

    @Test
    fun `クラスの重みと深さをモデルに渡す`() {
        val pipeline = buildPipeline(maxDepth = 5, classWeight = ClassWeight.BALANCED)

        assertEquals(DecisionTreeClassifier(maxDepth = 5, classWeight = ClassWeight.BALANCED), pipeline.model)
    }
}

class SaveAndLoadModelTest {
    private val directory: Path = createTempDirectory()

    @Test
    fun `保存したパイプラインを読み込むと同じ予測をする`() {
        val (x, t) = trainingData()
        val pipeline = buildPipeline(maxDepth = 3, classWeight = ClassWeight.NONE).fit(x, t)
        val modelFile = File(directory.toFile(), "model/survived.ser")

        saveModel(pipeline, modelFile)
        val loaded = loadModel(modelFile)

        assertEquals(listOf(1, 0), loaded.predict(newPassengers()))
    }

    @Test
    fun `許可していないクラスを含むファイルは読み込まない`() {
        val modelFile = File(directory.toFile(), "unknown.ser")
        ObjectOutputStream(modelFile.outputStream()).use { it.writeObject(java.awt.Point(1, 2)) }

        assertFailsWith<InvalidClassException> { loadModel(modelFile) }
    }
}

class EvaluateTest {
    @Test
    fun `正解率と見つけた生存者の数を求める`() {
        val (x, t) = trainingData()
        val split = TrainTestSplit(xTrain = x, xTest = newPassengers(), tTrain = t, tTest = listOf(1, 1))
        val pipeline = buildPipeline(maxDepth = 3, classWeight = ClassWeight.NONE).fit(x, t)

        val evaluation = evaluate(pipeline, split)

        assertEquals(Evaluation(trainAccuracy = 1.0, testAccuracy = 0.5, foundSurvivors = 1, survivors = 2), evaluation)
    }
}

class SurvivedDataTest {
    private val csvFile = File(dataDir(), "Survived.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ Survived.csv が配置されていない（gulp data:setup）")
    }

    private fun survivedSplit(): TrainTestSplit<Int> {
        val (x, t) = splitFeaturesAndTarget(loadSurvived(csvFile))
        return splitTrainTest(x, t, testSize = 0.2, seed = 0)
    }

    private fun evaluateWith(
        split: TrainTestSplit<Int>,
        maxDepth: Int,
        classWeight: ClassWeight,
    ): Evaluation = evaluate(buildPipeline(maxDepth, classWeight).fit(split.xTrain, split.tTrain), split)

    @Test
    fun `実データの件数と欠損値の数を確認する`() {
        val df = loadSurvived(csvFile)

        assertEquals(891, df.rowsCount())
        assertEquals(
            mapOf("Age" to 177, "Cabin" to 687, "Embarked" to 2),
            listOf("Age", "Cabin", "Embarked").associateWith { name ->
                df[name].values().count {
                    it ==
                        null
                }
            },
        )
    }

    @Test
    fun `深さ2ではbalancedにすると見つけられる生存者が増える`() {
        val split = survivedSplit()

        assertEquals(33, evaluateWith(split, maxDepth = 2, classWeight = ClassWeight.NONE).foundSurvivors)
        assertEquals(51, evaluateWith(split, maxDepth = 2, classWeight = ClassWeight.BALANCED).foundSurvivors)
    }

    @Test
    fun `深さ5ではbalancedにしても見つけられる生存者は増えない`() {
        val split = survivedSplit()

        val none = evaluateWith(split, maxDepth = 5, classWeight = ClassWeight.NONE)
        val balanced = evaluateWith(split, maxDepth = 5, classWeight = ClassWeight.BALANCED)

        assertEquals(46 to 45, none.foundSurvivors to balanced.foundSurvivors)
        assertEquals(0.832, balanced.testAccuracy, absoluteTolerance = 1e-3)
    }

    @Test
    fun `深さ5までならTribuoの決定木と前処理後のテストデータの予測が一致する`() {
        val split = survivedSplit()

        for (maxDepth in 1..5) {
            val pipeline = buildPipeline(maxDepth, ClassWeight.NONE).fit(split.xTrain, split.tTrain)
            val xTrain = pipeline.transform(split.xTrain)
            val xTest = pipeline.transform(split.xTest)
            val tribuo = predictWithTribuo(trainTribuoTree(xTrain, split.tTrain.map { it.toString() }, maxDepth, 1.0f), xTest)

            assertEquals(pipeline.predict(split.xTest).map { it.toString() }, tribuo, "深さ $maxDepth")
        }
    }

    @Test
    fun `実行すると評価結果を表示してモデルを保存する`() {
        val modelFile = File(createTempDirectory().toFile(), "survived.ser")

        val output = captureStdout { main(modelFile) }

        assertTrue(modelFile.exists())
        assertEquals(
            "データ件数: 891（生存 342, 死亡 549）\n" +
                "訓練データ: 712 件, テストデータ: 179 件\n" +
                "classWeight=NONE: 訓練 0.851, テスト 0.832, 生存者 68 人中 46 人を発見\n" +
                "classWeight=BALANCED: 訓練 0.850, テスト 0.832, 生存者 68 人中 45 人を発見\n" +
                "保存したモデル: survived.ser\n" +
                "架空の乗客の予測: [1, 0]\n",
            output,
        )
    }

    @Test
    fun `重み付けなしなら深さ10まで第3章の決定木と前処理後のテストデータの予測が一致する`() {
        val split = survivedSplit()

        for (maxDepth in 1..10) {
            val pipeline = buildPipeline(maxDepth, ClassWeight.NONE).fit(split.xTrain, split.tTrain)
            val xTrain = pipeline.transform(split.xTrain)
            val xTest = pipeline.transform(split.xTest)
            val chapter03 = DecisionTree(maxDepth).fit(xTrain, split.tTrain.map { it.toString() }).predict(xTest)

            assertEquals(pipeline.predict(split.xTest).map { it.toString() }, chapter03, "深さ $maxDepth")
        }
    }
}
