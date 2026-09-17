package chapter01

import dataset.dataDir
import org.junit.jupiter.api.Assumptions.assumeTrue
import support.captureStdout
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val HEADER = "\uFEFF身長,体重,年代,派閥\n"

class LoadPeopleTest {
    private val directory: Path = createTempDirectory()

    private fun writeCsv(rows: String): File = File(directory.toFile(), "kvst.csv").apply { writeText(HEADER + rows) }

    @Test
    fun `BOM付きCSVを読み込んで人物のリストを返す`() {
        val csvFile = writeCsv("165,58,30,きのこ\n")

        val people = loadPeople(csvFile)

        assertEquals(listOf(Person(height = 165, weight = 58, ageGroup = 30, faction = "きのこ")), people)
    }

    @Test
    fun `複数行のCSVを読み込んで行の順に人物のリストを返す`() {
        val csvFile = writeCsv("161,52,20,きのこ\n183,74,50,たけのこ\n")

        val people = loadPeople(csvFile)

        assertEquals(
            listOf(
                Person(height = 161, weight = 52, ageGroup = 20, faction = "きのこ"),
                Person(height = 183, weight = 74, ageGroup = 50, faction = "たけのこ"),
            ),
            people,
        )
    }
}

class SplitFeaturesAndLabelsTest {
    @Test
    fun `人物のリストを特徴量と正解ラベルに分ける`() {
        val people =
            listOf(
                Person(height = 161, weight = 52, ageGroup = 20, faction = "きのこ"),
                Person(height = 183, weight = 74, ageGroup = 50, faction = "たけのこ"),
            )

        val (features, labels) = splitFeaturesAndLabels(people)

        assertEquals(
            listOf(
                Features(height = 161, weight = 52, ageGroup = 20),
                Features(height = 183, weight = 74, ageGroup = 50),
            ),
            features,
        )
        assertEquals(listOf("きのこ", "たけのこ"), labels)
    }
}

class PredictByRuleTest {
    @Test
    fun `20代ならきのこ派と判定する`() {
        val features = Features(height = 161, weight = 52, ageGroup = 20)

        assertEquals("きのこ", predictByRule(features))
    }

    @Test
    fun `20代以外ならたけのこ派と判定する`() {
        val features = Features(height = 183, weight = 74, ageGroup = 50)

        assertEquals("たけのこ", predictByRule(features))
    }
}

class AccuracyTest {
    @Test
    fun `すべての予測が正解なら正解率は1`() {
        assertEquals(1.0, accuracy(listOf("きのこ", "たけのこ"), listOf("きのこ", "たけのこ")))
    }

    @Test
    fun `4件中3件の予測が正解なら正解率は075`() {
        val predictions = listOf("きのこ", "きのこ", "たけのこ", "たけのこ")
        val labels = listOf("きのこ", "たけのこ", "たけのこ", "たけのこ")

        assertEquals(0.75, accuracy(predictions, labels))
    }

    @Test
    fun `予測と正解ラベルの件数が違えばエラーになる`() {
        assertFailsWith<IllegalArgumentException> {
            accuracy(listOf("きのこ"), listOf("きのこ", "たけのこ"))
        }
    }
}

class KvsTDataTest {
    private val csvFile = File(dataDir(), "KvsT.csv")

    @BeforeTest
    fun requireData() {
        assumeTrue(csvFile.exists(), "学習データ KvsT.csv が配置されていない（gulp data:setup）")
    }

    @Test
    fun `実データから19人分を読み込む`() {
        assertEquals(19, loadPeople(csvFile).size)
    }

    @Test
    fun `ルールによる判定の正解率を実データで計算する`() {
        val (features, labels) = splitFeaturesAndLabels(loadPeople(csvFile))

        val predictions = features.map(::predictByRule)

        assertEquals(14.0 / 19, accuracy(predictions, labels), absoluteTolerance = 1e-12)
    }

    @Test
    fun `実行するとデータ件数と正解率を表示する`() {
        val output = captureStdout { main() }

        assertEquals("データ件数: 19\nルールによる判定の正解率: 0.7368\n", output)
    }
}
