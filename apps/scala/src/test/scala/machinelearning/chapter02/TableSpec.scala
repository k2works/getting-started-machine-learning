package machinelearning.chapter02

import java.nio.file.{Files, Path}
import org.scalatest.funsuite.AnyFunSuite

class TableSpec extends AnyFunSuite:
  private val header = "\uFEFFがく片長さ,がく片幅,花弁長さ,花弁幅,種類\n"

  private def writeCsv(rows: String): Path =
    val file = Files.createTempDirectory("ml-scala-").resolve("iris.csv")
    Files.writeString(file, header + rows)

  test("CSV を読み込むと列名の並びを保ち、BOM は残らない") {
    val table = Table.load(writeCsv("0.1,0.2,0.3,0.4,Iris-setosa\n"))

    assert(table.columns === Vector("がく片長さ", "がく片幅", "花弁長さ", "花弁幅", "種類"))
  }

  test("空欄は欠損値の None として読み込む") {
    val row = Table.load(writeCsv("0.1,,0.3,0.4,Iris-setosa\n")).rows.head

    assert(row.number("がく片幅") === None)
    assert(row.number("がく片長さ") === Some(0.1))
    assert(row.text("種類") === "Iris-setosa")
  }

  test("行の最後の列が空欄でも欠損値として読み込む") {
    val row = Table.load(writeCsv("0.1,0.2,0.3,,\n")).rows.head

    assert(row.number("花弁幅") === None)
    assert(row.text("種類") === "")
  }

  test("無い列を読み出すと列名を示すエラーになる") {
    val row = Row(Map("がく片長さ" -> "0.1"))

    val error = intercept[IllegalArgumentException](row.number("花弁幅"))
    assert(error.getMessage.contains("花弁幅"))
  }

  test("列ごとの欠損値の数を列の順に数える") {
    val table = Table.load(
      writeCsv("0.1,0.2,0.3,0.4,Iris-setosa\n,0.3,0.5,0.6,Iris-setosa\n,,0.7,0.8,Iris-virginica\n")
    )

    assert(
      table.countMissing === Vector("がく片長さ" -> 2, "がく片幅" -> 1, "花弁長さ" -> 0, "花弁幅" -> 0, "種類" -> 0)
    )
  }
