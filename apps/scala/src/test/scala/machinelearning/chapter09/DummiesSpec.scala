package machinelearning.chapter09

import machinelearning.chapter02.Table
import org.scalatest.funsuite.AnyFunSuite

class DummiesSpec extends AnyFunSuite:
  private def crimeTable(crimes: String*): Table =
    Samples.table(
      Vector("CRIME", "RM"),
      crimes.map(crime => Samples.row("CRIME" -> crime, "RM" -> "6.0"))*
    )

  test("先頭を除いたカテゴリを辞書順に返す") {
    assert(
      Dummies.categories(Vector("low", "high", "very_low", "low")) === Vector("low", "very_low")
    )
  }

  test("欠損値（空欄）はカテゴリに数えない") {
    assert(Dummies.categories(Vector("low", "", "high")) === Vector("low"))
  }

  test("カテゴリごとに 0 と 1 の列を作り元の列を取り除く") {
    val encoded =
      Dummies.encode(crimeTable("low", "high", "very_low"), "CRIME", Vector("low", "very_low"))

    assert(encoded.columns === Vector("RM", "CRIME_low", "CRIME_very_low"))
    assert(encoded.rows.map(_.text("RM")) === Vector("6.0", "6.0", "6.0"))
    assert(encoded.rows.map(_.text("CRIME_low")) === Vector("1", "0", "0"))
    assert(encoded.rows.map(_.text("CRIME_very_low")) === Vector("0", "0", "1"))
  }

  test("カテゴリに無い値はすべての列が 0 になる") {
    val encoded = Dummies.encode(crimeTable("unknown"), "CRIME", Vector("low", "very_low"))

    assert(encoded.rows.head.text("CRIME_low") === "0")
    assert(encoded.rows.head.text("CRIME_very_low") === "0")
  }
