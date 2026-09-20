package machinelearning.chapter08

import machinelearning.chapter02.Features
import machinelearning.chapter03.{DecisionTree, DecisionTrees}
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class DecisionTreeClassifierSpec extends AnyFunSuite:

  private def fareAndAge(rows: (Double, Double)*): Vector[Features] =
    rows.toVector.map((fare, age) => Features(Vector("Fare", "Age"), Vector(fare, age)))

  private def column(name: String, values: Double*): Vector[Features] =
    values.toVector.map(value => Features(Vector(name), Vector(value)))

  test("重みがすべて 1 なら第 3 章のジニ不純度と同じ値になる") {
    val labels = Vector(0, 1, 1)

    assert(
      WeightedTrees.weightedGini(labels, Vector(1.0, 1.0, 1.0))
        === DecisionTrees.gini(labels.map(_.toString))
    )
  }

  test("重みの大きいラベルほど多いものとして不純度を計算する") {
    assert(WeightedTrees.weightedGini(Vector(0, 1), Vector(1.0, 3.0)) === 0.375 +- 1e-12)
  }

  test("少ないクラスほど 1 件の重みを大きくし、クラスごとの重みの合計をそろえる") {
    val weights = WeightedTrees.balancedWeights(Vector(0, 0, 0, 1))

    assert(weights === Vector(4.0 / 6, 4.0 / 6, 4.0 / 6, 2.0))
    assert(weights.take(3).sum === weights(3) +- 1e-12)
  }

  test("重み付けなしなら第 3 章の決定木と同じ予測をする") {
    val x = fareAndAge((8, 30), (9, 22), (13, 18), (20, 45), (60, 25), (80, 33))
    val t = Vector(0, 0, 1, 0, 1, 1)

    Vector(Some(1), Some(2), None).foreach { maxDepth =>
      val chapter03 = maxDepth.fold(DecisionTree.unlimited())(DecisionTree.withMaxDepth)
      val expected = chapter03.fit(x, t.map(_.toString)).predict(x)

      val predictions =
        DecisionTreeClassifier(maxDepth, ClassWeight.Unweighted).fit(x, t).predict(x)

      assert(predictions.map(_.toString) === expected, s"深さ $maxDepth")
    }
  }

  test("balanced にすると、少ないクラスが混ざった葉でも少ないクラスを予測する") {
    val fare = column("Fare", 1, 1, 1, 1, 2, 2, 2)
    val survived = Vector(0, 0, 0, 0, 0, 0, 1)
    val newX = column("Fare", 1, 2)

    val unweighted = DecisionTreeClassifier(Some(1), ClassWeight.Unweighted).fit(fare, survived)
    val balanced = DecisionTreeClassifier(Some(1), ClassWeight.Balanced).fit(fare, survived)

    assert(unweighted.predict(newX) === Vector(0, 0))
    assert(balanced.predict(newX) === Vector(0, 1))
  }
