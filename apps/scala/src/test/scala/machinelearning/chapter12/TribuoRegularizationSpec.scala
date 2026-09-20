package machinelearning.chapter12

import com.oracle.labs.mlrg.olcut.config.PropertyException
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite
import org.tribuo.regression.slm.ElasticNetCDTrainer

class TribuoRegularizationSpec extends AnyFunSuite:

  test("ラッソ回帰では予測に役立たない特徴量の係数が 0 になる") {
    val (x, t) = Samples.sparseDataset()

    val model = TribuoRegularization.fitLasso(x, t, 0.5)

    assert(
      ModelSelection.zeroCoefficientNames(
        model.coefficients,
        Vector("x1", "x2", "noise1", "noise2")
      ) === Vector("noise1", "noise2")
    )
  }

  test("ElasticNetCDTrainer は l1Ratio が 0 のリッジ回帰を受け付けない") {
    val thrown = intercept[PropertyException](ElasticNetCDTrainer(0.5, 0.0))

    assert(thrown.getMessage.endsWith("L1 Ratio must be between 0 and 1. Found value 0.0"))
  }

  test("ElasticNetCDTrainer は l1Ratio の下限の 1e-12 を受け付け、1e-13 を受け付けない") {
    assert(ElasticNetCDTrainer(0.5, TribuoRegularization.MinL1Ratio) !== null)
    assert(intercept[PropertyException](ElasticNetCDTrainer(0.5, 1e-13)).getMessage.nonEmpty)
  }

  test("l1Ratio を下限まで小さくし alpha を件数で割ると自作のリッジ回帰と同じ係数と切片になる") {
    val (x, t) = Samples.randomDataset()

    val model = TribuoRegularization.fitRidge(x, t, 10.0)
    val expected = Ridge.fit(x, t, 10.0)

    expected.coefficients.lazyZip(model.coefficients).foreach { (a, b) =>
      assert(b === a +- 1e-6)
    }
    assert(model.intercept === expected.intercept +- 1e-6)
  }
