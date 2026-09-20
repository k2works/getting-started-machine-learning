package machinelearning.chapter13

import machinelearning.chapter02.{Row, Table}
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class BostonPcaSpec extends AnyFunSuite:
  private def row(crime: String, rm: String, price: String): Row =
    Row(Map("CRIME" -> crime, "RM" -> rm, "PRICE" -> price))

  /** 架空の 4 件。RM の 3 件目が欠損値。 */
  private def bostonLike(): Table =
    Table(
      Vector("CRIME", "RM", "PRICE"),
      Vector(
        row("high", "5", "10"),
        row("low", "6", "20"),
        row("very_low", "", "30"),
        row("low", "7", "40")
      )
    )

  test("CRIME をダミー変数の列に置き換える") {
    val x = BostonPca.standardize(bostonLike())

    assert(x.head.columns === Vector("RM", "PRICE", "CRIME_low", "CRIME_very_low"))
  }

  test("欠損値を補完してから各列を平均 0 と標準偏差 1 にそろえる") {
    val x = BostonPca.standardize(bostonLike())

    x.head.columns.foreach { column =>
      val values = x.map(_.value(column))
      val mean = values.sum / values.size
      val variance = values.map(v => (v - mean) * (v - mean)).sum / values.size
      assert(mean === 0.0 +- 1e-9, column)
      assert(math.sqrt(variance) === 1.0 +- 1e-9, column)
    }
  }
