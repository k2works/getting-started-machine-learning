package machinelearning.chapter12

import machinelearning.chapter02.{Row, Table}
import org.scalatest.funsuite.AnyFunSuite

class BostonSpec extends AnyFunSuite:
  private def row(rm: String, ptratio: String, lstat: String, price: String): Row =
    Row(Map("RM" -> rm, "PTRATIO" -> ptratio, "LSTAT" -> lstat, "PRICE" -> price))

  test("z スコアの絶対値が閾値を超える値を持つ行を除く") {
    // 最後の 1 行だけが RM の平均から大きく離れている
    val rows = Vector.fill(9)(row("6", "18", "12", "22")) :+ row("60", "18", "12", "22")
    val table = Table(Vector("RM", "PTRATIO", "LSTAT", "PRICE"), rows)

    val kept = Boston.removeOutliers(table, Boston.OutlierColumns, 2.0)

    assert(kept.rows.size === 9)
  }

  test("閾値より小さい z スコアの行は残す") {
    val rows =
      Vector(row("6", "18", "12", "22"), row("7", "19", "13", "24"), row("5", "17", "11", "20"))
    val table = Table(Vector("RM", "PTRATIO", "LSTAT", "PRICE"), rows)

    assert(Boston.removeOutliers(table, Boston.OutlierColumns, 3.0).rows.size === 3)
  }

  test("外れ値を調べる列は特徴量と正解") {
    assert(Boston.OutlierColumns === Vector("RM", "PTRATIO", "LSTAT", "PRICE"))
  }
