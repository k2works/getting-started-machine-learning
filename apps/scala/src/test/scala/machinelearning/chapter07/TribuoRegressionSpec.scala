package machinelearning.chapter07

import java.util.Random
import machinelearning.chapter02.Features
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite
import org.tribuo.regression.Regressor
import org.tribuo.regression.evaluation.RegressionEvaluator
import org.tribuo.regression.slm.{LARSTrainer, SLMTrainer, SparseLinearModel}
import scala.jdk.CollectionConverters.*

class TribuoRegressionSpec extends AnyFunSuite:
  private val columns = Vector("a", "b", "c")
  private val size = 30

  /** t = 4 + 1.5a - 0.5b + 2c に正規分布のノイズを加えた 30 件の架空のデータ。 */
  private def noisyDataset(): (Vector[Features], Vector[Double]) =
    val random = Random(0)
    val values = Vector.fill(columns.size, size)(random.nextDouble() * 10)
    val x = (0 until size).toVector.map(i => Features(columns, values.map(_(i))))
    val t = x.map(row =>
      4 + 1.5 * row.value("a") - 0.5 * row.value("b") + 2 * row.value("c") + random.nextGaussian()
    )
    (x, t)

  /** 平均との差の 2 乗の合計の平方根。 */
  private def centeredNorm(values: Vector[Double]): Double =
    val mean = values.sum / values.size
    math.sqrt(values.map(v => (v - mean) * (v - mean)).sum)

  test("特徴量の行を数値の正解ラベル付きの事例に変換する") {
    val names = Vector("SNS1", "actor")
    val x = Vector(Features(names, Vector(100.0, 9000.0)), Features(names, Vector(200.0, 9500.0)))

    val dataset = TribuoRegression.toDataset(x, Vector(9200.0, 9800.0))

    assert(dataset.size === 2)
    assert(dataset.getFeatureIDMap.keySet.asScala.toSet === Set("SNS1", "actor"))
    assert(
      dataset.getData.asScala.toVector.map(_.getOutput.getValues()(0)) === Vector(9200.0, 9800.0)
    )
  }

  test("SLMTrainer(true) の予測は自作の線形回帰の予測と一致する") {
    val (x, t) = noisyDataset()

    val model = TribuoRegression.train(SLMTrainer(true), x, t)

    val mine = LinearRegression.fit(x, t).predict(x)
    assert(
      TribuoRegression.predict(model, x).lazyZip(mine).forall((a, b) => a === b +- 1e-9)
    )
  }

  test("LARSTrainer の予測も自作の線形回帰の予測と一致する") {
    val (x, t) = noisyDataset()

    val model = TribuoRegression.train(LARSTrainer(), x, t)

    val mine = LinearRegression.fit(x, t).predict(x)
    assert(
      TribuoRegression.predict(model, x).lazyZip(mine).forall((a, b) => a === b +- 1e-9)
    )
  }

  test("Tribuo の重みは正規化した空間の値で、元の単位に戻すと自作の係数と一致する") {
    val (x, t) = noisyDataset()
    val model = TribuoRegression.train(SLMTrainer(true), x, t).asInstanceOf[SparseLinearModel]
    val weights = model.getWeights.values.iterator.next
    val model2 = LinearRegression.fit(x, t)

    columns.foreach { name =>
      val column = x.map(_.value(name))
      val weight = weights.get(model.getFeatureIDMap.getID(name))
      assert(math.abs(weight - model2.coefficient(name)) > 1e-3, s"$name の重み")
      assert(weight * centeredNorm(t) / centeredNorm(column) === model2.coefficient(name) +- 1e-9)
    }
  }

  test("Tribuo の評価器の MAE・RMSE・R² は自作の評価指標と一致する") {
    val (x, t) = noisyDataset()
    val model = TribuoRegression.train(SLMTrainer(true), x, t)
    val y = TribuoRegression.predict(model, x)

    val evaluation = RegressionEvaluator().evaluate(model, TribuoRegression.toDataset(x, t))

    val target = Regressor(TribuoRegression.OutputName, Double.NaN)
    assert(evaluation.mae(target) === RegressionMetrics.meanAbsoluteError(t, y) +- 1e-9)
    assert(evaluation.rmse(target) === RegressionMetrics.rootMeanSquaredError(t, y) +- 1e-9)
    assert(evaluation.r2(target) === RegressionMetrics.r2Score(t, y) +- 1e-9)
  }
