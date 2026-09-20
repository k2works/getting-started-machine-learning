package machinelearning.chapter09

import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class TribuoStandardizationSpec extends AnyFunSuite:
  private val Train = Vector(5.5, 6.0, 7.5, 6.5)
  private val Values = Vector(6.2, 8.0)

  test("Tribuo の MeanStdDevTransformation は件数から 1 を引いて割る標準偏差を使う") {
    val mean = Train.sum / Train.size
    val sumOfSquares = Train.map(value => (value - mean) * (value - mean)).sum
    val sampleStd = math.sqrt(sumOfSquares / (Train.size - 1))

    val standardized = TribuoStandardization.standardize(Train, Values)

    assert(standardized(0) === (6.2 - mean) / sampleStd +- 1e-12)
    assert(standardized(1) === (8.0 - mean) / sampleStd +- 1e-12)
  }

  test("自作の標準化に件数から決まる係数を掛けると Tribuo の値になる") {
    val standardizer = Standardizer.fit(Train.map(Samples.rm))
    val ratio = math.sqrt((Train.size - 1.0) / Train.size)

    val tribuo = TribuoStandardization.standardize(Train, Values)

    Values.zip(tribuo).foreach { (value, expected) =>
      assert(standardizer.transform(Samples.rm(value)).value("RM") * ratio === expected +- 1e-12)
    }
  }
