package machinelearning.chapter10

import java.util.Random
import machinelearning.chapter02.Features
import machinelearning.chapter03.Samples
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class LogisticRegressionSpec extends AnyFunSuite:
  test("ソフトマックスは合計が 1 になる確率にする") {
    val probabilities = LogisticRegression.softmax(Vector(1.0, 2.0, 3.0))

    assert(probabilities.sum === 1.0 +- 1e-12)
    assert(probabilities(2) > probabilities(1) && probabilities(1) > probabilities(0))
  }

  test("大きな値でもあふれない") {
    val probabilities = LogisticRegression.softmax(Vector(1000.0, 1001.0))

    assert(probabilities.forall(p => !p.isNaN))
    assert(probabilities.sum === 1.0 +- 1e-12)
  }

  test("正解の確率が高いほど交差エントロピーは小さい") {
    val confident = LogisticRegression.crossEntropy(Vector(Vector(0.9, 0.1)), Vector(0))
    val unsure = LogisticRegression.crossEntropy(Vector(Vector(0.5, 0.5)), Vector(0))

    assert(confident < unsure)
  }

  test("分けられるデータを学習すると訓練データを正しく予測する") {
    val model = LogisticRegression(learningRate = 1.0, epochs = 500)
      .fit(Samples.threeSpeciesX, Samples.threeSpeciesT)

    assert(model.predict(Samples.threeSpeciesX) === Samples.threeSpeciesT)
  }

  test("学習した品種は名前の順に並ぶ") {
    val model = LogisticRegression(epochs = 10).fit(Samples.threeSpeciesX, Samples.threeSpeciesT)

    assert(model.learnedClasses === Vector("setosa", "versicolor", "virginica"))
  }

  test("繰り返すほど損失は小さくなる") {
    val model = LogisticRegression(epochs = 100).fit(Samples.threeSpeciesX, Samples.threeSpeciesT)

    assert(model.losses.size === 100)
    assert(model.losses.last < model.losses.head)
  }

  test("学習する前に予測するとエラーになる") {
    assertThrows[IllegalStateException](LogisticRegression().predict(Samples.threeSpeciesX))
  }

class RandomForestSpec extends AnyFunSuite:
  test("同数でなければ多数派の予測を選ぶ") {
    val votes = Vector(Vector("a", "b"), Vector("a", "b"), Vector("c", "a"))

    assert(RandomForest.majorityVote(votes) === Vector("a", "b"))
  }

  test("同数なら先に現れた予測を選ぶ") {
    assert(RandomForest.majorityVote(Vector(Vector("b"), Vector("a"))) === Vector("b"))
  }

  test("ブートストラップ標本は重複を許して同じ件数を選ぶ") {
    val sample = RandomForest.bootstrapSample(10, Random(0))

    assert(sample.size === 10)
    assert(sample.forall(i => i >= 0 && i < 10))
  }

  test("特徴量から指定した列だけを取り出す") {
    val features = Features(Vector("a", "b", "c"), Vector(0.1, 0.2, 0.3))

    assert(
      RandomForest.selectColumns(features, Vector("a", "c")) === Features(
        Vector("a", "c"),
        Vector(0.1, 0.3)
      )
    )
  }

  test("同じシードなら同じ森になる") {
    val first = RandomForest.of(5, 1, 0).fit(Samples.threeSpeciesX, Samples.threeSpeciesT)
    val second = RandomForest.of(5, 1, 0).fit(Samples.threeSpeciesX, Samples.threeSpeciesT)

    assert(first.predict(Samples.threeSpeciesX) === second.predict(Samples.threeSpeciesX))
    assert(first.trees.map(_.rows) === second.trees.map(_.rows))
  }

  test("学習する前に予測するとエラーになる") {
    assertThrows[IllegalStateException](RandomForest.of(5, 1, 0).predict(Samples.threeSpeciesX))
  }

class FeatureImportanceSpec extends AnyFunSuite:
  test("1 本の木の重要度は合計が 1 になる") {
    val tree = machinelearning.chapter03.DecisionTree
      .unlimited()
      .fit(Samples.threeSpeciesX, Samples.threeSpeciesT)

    val importances =
      FeatureImportance.treeImportances(
        tree.fitted.get,
        Samples.threeSpeciesX,
        Samples.threeSpeciesT
      )

    assert(importances.values.sum === 1.0 +- 1e-12)
  }

  test("分割に使われない特徴量の重要度は 0") {
    val columns = Vector("使う", "使わない")
    val x = Vector(
      Features(columns, Vector(0.1, 0.5)),
      Features(columns, Vector(0.9, 0.5))
    )
    val tree = machinelearning.chapter03.DecisionTree.unlimited().fit(x, Vector("左", "右"))

    val importances = FeatureImportance.treeImportances(tree.fitted.get, x, Vector("左", "右"))

    assert(importances("使わない") === 0.0)
    assert(importances("使う") === 1.0 +- 1e-12)
  }
