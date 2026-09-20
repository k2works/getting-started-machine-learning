package machinelearning.chapter14

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import machinelearning.chapter02.Features
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.immutable.SeqMap

class SpendingSpec extends AnyFunSuite:
  private val Header = "Channel,Region,Fresh,Milk,Grocery,Frozen,Detergents_Paper,Delicassen\n"

  private def writeCsv(content: String): Path =
    val csv = Files.createTempFile("wholesale", ".csv")
    csv.toFile.deleteOnExit()
    Files.write(csv, content.getBytes(StandardCharsets.UTF_8))

  test("Channel と Region を除いた支出額の列を読み込む") {
    val csv = writeCsv(Header + "1,2,100,200,300,400,500,600\n")

    val x = Spending.load(csv)

    assert(
      x.head.columns === Vector(
        "Fresh",
        "Milk",
        "Grocery",
        "Frozen",
        "Detergents_Paper",
        "Delicassen"
      )
    )
    assert(x.head.values === Vector(100.0, 200.0, 300.0, 400.0, 500.0, 600.0))
  }

  test("列ごとに平均 0・標準偏差 1 の点に変換する") {
    val columns = Vector("Fresh", "Milk")
    val x = Vector(
      Features(columns, Vector(10, 5)),
      Features(columns, Vector(20, 5)),
      Features(columns, Vector(30, 8))
    )

    val points = Spending.standardize(x)

    points.transpose.foreach { values =>
      val mean = values.sum / values.size
      val variance = values.map(v => (v - mean) * (v - mean)).sum / values.size
      assert(mean === 0.0 +- 1e-12)
      assert(math.sqrt(variance) === 1.0 +- 1e-12)
    }
  }

  test("クラスタごとの件数と平均を件数の多い順に並べる") {
    val columns = Vector("Fresh", "Milk")
    val x = Vector(
      Features(columns, Vector(100, 20)),
      Features(columns, Vector(300, 40)),
      Features(columns, Vector(1000, 900))
    )

    val summary = Spending.summarizeClusters(x, Vector(1, 1, 0))

    assert(
      summary === Vector(
        ClusterSummary(1, 2, SeqMap("Fresh" -> 200.0, "Milk" -> 30.0)),
        ClusterSummary(0, 1, SeqMap("Fresh" -> 1000.0, "Milk" -> 900.0))
      )
    )
  }
