package machinelearning.chapter09

import machinelearning.chapter02.{Features, TrainTestSplit}
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class OutliersSpec extends AnyFunSuite:
  test("四分位数の位置が値の間にあれば前後の値から線形補間する") {
    assert(Outliers.quantile(Vector(4.0, 1.0, 3.0, 2.0), 0.25) === 1.75 +- 1e-12)
  }

  test("第 3 四分位数から IQR の 1.5 倍より大きい値を外れ値とする") {
    assert(
      Outliers.iqrOutliers(Vector(1.0, 2.0, 3.0, 4.0, 100.0)) ===
        Vector(false, false, false, false, true)
    )
  }

  test("第 1 四分位数から IQR の 1.5 倍より小さい値も外れ値とする") {
    assert(
      Outliers.iqrOutliers(Vector(-100.0, 1.0, 2.0, 3.0, 4.0)) ===
        Vector(true, false, false, false, false)
    )
  }

  test("訓練データから価格が外れ値の行を取り除きテストデータは残す") {
    val split = TrainTestSplit[Features, Double](
      Vector(5.0, 6.0, 6.5, 7.0, 8.0).map(Samples.rm),
      Vector(Samples.rm(9)),
      Vector(1.0, 2.0, 3.0, 4.0, 100.0),
      Vector(500.0)
    )

    val removed = Outliers.removeTargetOutliers(split)

    assert(removed.xTrain.map(_.value("RM")) === Vector(5.0, 6.0, 6.5, 7.0))
    assert(removed.tTrain === Vector(1.0, 2.0, 3.0, 4.0))
    assert(removed.xTest === split.xTest)
    assert(removed.tTest === Vector(500.0))
  }
