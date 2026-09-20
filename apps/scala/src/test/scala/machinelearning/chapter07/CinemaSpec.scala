package machinelearning.chapter07

import java.nio.file.{Files, Path}
import machinelearning.chapter02.{Row, Table}
import org.scalatest.funsuite.AnyFunSuite

class CinemaSpec extends AnyFunSuite:
  private val header = "cinema_id,SNS1,SNS2,actor,original,sales\n"

  /** SNS2 と興行収入だけを持つ表を作る。 */
  private def table(sns2AndSales: (String, String)*): Table =
    Table(
      Vector("SNS2", "sales"),
      sns2AndSales.toVector.map((sns2, sales) => Row(Map("SNS2" -> sns2, "sales" -> sales)))
    )

  private def sns2(table: Table): Vector[String] = table.rows.map(_.text("SNS2"))

  /** 一時ディレクトリに CSV を書き、その場所を渡す。 */
  private def withCsvFile(contents: String)(use: Path => Unit): Unit =
    val directory = Files.createTempDirectory("cinema")
    val csvFile = directory.resolve("cinema.csv")
    try
      val _ = Files.writeString(csvFile, contents)
      use(csvFile)
    finally
      val _ = Files.deleteIfExists(csvFile)
      val _ = Files.deleteIfExists(directory)

  test("SNS2 が 1000 を超え売上が 8500 未満の行を取り除く") {
    val rows = table("1200" -> "8000", "600" -> "9500")

    assert(sns2(Cinema.removeOutliers(rows)) === Vector("600"))
  }

  test("条件の片方だけを満たす行は残す") {
    val rows = table("1200" -> "9800", "600" -> "8000")

    assert(sns2(Cinema.removeOutliers(rows)) === Vector("1200", "600"))
  }

  test("外れ値を除き特徴量を選んで分割し欠損値を補完する") {
    withCsvFile(
      header +
        "1,100,300,9000.0,0,9200\n" +
        "2,,400,9500.0,1,9800\n" +
        "3,300,500,,1,10100\n" +
        "4,150,1200,8800.0,0,8100\n" +
        "5,250,700,9900.0,1,10300\n" +
        "6,120,650,9100.0,0,9400\n"
    ) { csvFile =>
      val split = Cinema.prepare(csvFile, 0.4, 0)

      assert(split.xTrain.head.columns === Vector("SNS1", "SNS2", "actor", "original"))
      assert((split.xTrain.size, split.xTest.size) === (3, 2))
      assert(!split.tTrain.contains(8100.0))
      assert(!split.tTest.contains(8100.0))
      val _ = assert(split.xTrain.forall(_.values.forall(!_.isNaN)))
    }
  }
