package machinelearning.chapter12

import machinelearning.chapter07.Matrix
import org.scalatest.funsuite.AnyFunSuite

class ModelSelectionSpec extends AnyFunSuite:
  private val xTrain = Matrix(
    Vector(Vector(1.0, 1.0), Vector(2.0, 4.0), Vector(3.0, 9.0), Vector(4.0, 16.0))
  )
  private val tTrain = Vector(3.0, 8.0, 15.0, 24.0)
  private val xValid = Matrix(Vector(Vector(5.0, 25.0), Vector(6.0, 36.0)))
  private val tValid = Vector(35.0, 48.0)

  private def experiment(alpha: Double, validationScore: Double): Experiment =
    Experiment(alpha, 0.9, validationScore, 1.0)

  test("正則化の強さごとに 1 件ずつ実験結果を記録する") {
    val alphas = Vector(0.0, 1.0, 10.0)

    val experiments = ModelSelection.runRidgeExperiments(xTrain, tTrain, xValid, tValid, alphas)

    assert(experiments.map(_.alpha) === alphas)
  }

  test("正則化を強めると訓練データの決定係数は下がり係数の絶対値の合計も小さくなる") {
    val experiments =
      ModelSelection.runRidgeExperiments(xTrain, tTrain, xValid, tValid, Vector(0.0, 100.0))

    assert(experiments.head.trainScore > experiments(1).trainScore)
    assert(experiments.head.coefficientAbsSum > experiments(1).coefficientAbsSum)
  }

  test("検証データの決定係数が最も高い実験を選ぶ") {
    val experiments = Vector(experiment(0.0, 0.5), experiment(1.0, 0.8))

    assert(ModelSelection.bestExperiment(experiments).alpha === 1.0)
  }

  test("最も高い実験が途中にあってもそれを選ぶ") {
    val experiments = Vector(experiment(0.0, 0.5), experiment(1.0, 0.9), experiment(10.0, 0.7))

    assert(ModelSelection.bestExperiment(experiments).alpha === 1.0)
  }

  test("検証データの決定係数が同じなら先の実験を選ぶ") {
    val experiments = Vector(experiment(0.0, 0.8), experiment(1.0, 0.8))

    assert(ModelSelection.bestExperiment(experiments).alpha === 0.0)
  }

  test("実験結果が 1 件も無ければエラーになる") {
    assert(
      intercept[IllegalArgumentException](
        ModelSelection.bestExperiment(Vector.empty)
      ).getMessage.nonEmpty
    )
  }

  test("0 になった係数の特徴量名を返す") {
    val names = Vector("a", "b", "c")

    assert(ModelSelection.zeroCoefficientNames(Vector(1.0, 0.0, -0.5), names) === Vector("b"))
  }

  test("係数と特徴量名の数が違えばエラーになる") {
    assert(
      intercept[IllegalArgumentException](
        ModelSelection.zeroCoefficientNames(Vector(1.0), Vector("a", "b"))
      ).getMessage.nonEmpty
    )
  }
