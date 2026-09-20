package machinelearning.chapter09

import machinelearning.chapter02.Features
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite
import scala.collection.immutable.SeqMap

class StandardizerSpec extends AnyFunSuite:
  private val Columns = Vector("RM", "LSTAT")

  private def row(rm: Double, lstat: Double): Features = Features(Columns, Vector(rm, lstat))

  test("訓練データから列ごとの平均と標準偏差を求める") {
    val standardizer = Standardizer.fit(Vector(row(1, 10), row(2, 10), row(3, 40)))

    assert(standardizer.means("RM") === 2.0 +- 1e-12)
    assert(standardizer.means("LSTAT") === 20.0 +- 1e-12)
    assert(standardizer.stds("RM") === math.sqrt(2.0 / 3) +- 1e-12)
    assert(standardizer.stds("LSTAT") === math.sqrt(200.0) +- 1e-12)
  }

  test("平均と標準偏差は列の順を保つ") {
    val standardizer = Standardizer.fit(Vector(row(1, 10), row(2, 40)))

    assert(standardizer.means.keys.toVector === Columns)
  }

  test("訓練データの平均と標準偏差で別のデータを標準化する") {
    val standardizer = Standardizer(SeqMap("RM" -> 2.0), SeqMap("RM" -> 0.5))
    val other = Vector(1.0, 2.0, 4.0).map(Samples.rm)

    val standardized = standardizer.transform(other)

    assert(standardized.map(_.value("RM")) === Vector(-2.0, 0.0, 4.0))
  }

  test("すべて同じ値の列は 0 にする") {
    val x = Vector(1.0, 1.0, 1.0).map(Samples.rm)

    val standardized = Standardizer.fit(x).transform(x)

    assert(standardized.map(_.value("RM")) === Vector(0.0, 0.0, 0.0))
  }

  test("標準化する列に無い列はそのまま残す") {
    val standardizer = Standardizer(SeqMap("RM" -> 2.0), SeqMap("RM" -> 0.5))

    assert(standardizer.transform(row(3, 7)) === row(2, 7))
  }

  test("平均と標準偏差の列が違えば作れない") {
    assertThrows[IllegalArgumentException] {
      Standardizer(SeqMap("RM" -> 2.0), SeqMap("LSTAT" -> 0.5))
    }
  }
