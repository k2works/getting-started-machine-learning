package machinelearning.chapter01

import java.nio.file.{Files, Path}
import org.scalatest.funsuite.AnyFunSuite

class KinokoTakenokoSpec extends AnyFunSuite:
  private val header = "\uFEFF身長,体重,年代,派閥\n"

  private def writeCsv(rows: String): Path =
    val file = Files.createTempDirectory("ml-scala-").resolve("kvst.csv")
    Files.writeString(file, header + rows)

  test("BOM 付き CSV を読み込んで人物のリストを返す") {
    val people = KinokoTakenoko.loadPeople(writeCsv("165,58,30,きのこ\n"))

    assert(people === Vector(Person(165, 58, 30, "きのこ")))
  }

  test("複数行の CSV を読み込んで行の順に人物のリストを返す") {
    val people = KinokoTakenoko.loadPeople(writeCsv("161,52,20,きのこ\n183,74,50,たけのこ\n"))

    assert(people === Vector(Person(161, 52, 20, "きのこ"), Person(183, 74, 50, "たけのこ")))
  }

  test("人物のリストを特徴量と正解ラベルに分ける") {
    val people = Vector(Person(161, 52, 20, "きのこ"), Person(183, 74, 50, "たけのこ"))

    val (features, labels) = KinokoTakenoko.splitFeaturesAndLabels(people)

    assert(features === Vector(Features(161, 52, 20), Features(183, 74, 50)))
    assert(labels === Vector("きのこ", "たけのこ"))
  }

  test("20 代ならきのこ派と判定する") {
    assert(KinokoTakenoko.predictByRule(Features(161, 52, 20)) === "きのこ")
  }

  test("20 代以外ならたけのこ派と判定する") {
    assert(KinokoTakenoko.predictByRule(Features(183, 74, 50)) === "たけのこ")
  }

  test("すべての予測が正解なら正解率は 1") {
    assert(KinokoTakenoko.accuracy(Vector("きのこ", "たけのこ"), Vector("きのこ", "たけのこ")) === 1.0)
  }

  test("4 件中 3 件の予測が正解なら正解率は 0.75") {
    val predictions = Vector("きのこ", "きのこ", "たけのこ", "たけのこ")
    val labels = Vector("きのこ", "たけのこ", "たけのこ", "たけのこ")

    assert(KinokoTakenoko.accuracy(predictions, labels) === 0.75)
  }

  test("予測と正解ラベルの件数が違えばエラーになる") {
    assertThrows[IllegalArgumentException] {
      KinokoTakenoko.accuracy(Vector("きのこ"), Vector("きのこ", "たけのこ"))
    }
  }
