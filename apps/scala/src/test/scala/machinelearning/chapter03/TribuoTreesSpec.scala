package machinelearning.chapter03

import machinelearning.chapter02.Features
import org.scalatest.funsuite.AnyFunSuite
import scala.jdk.CollectionConverters.*

class TribuoTreesSpec extends AnyFunSuite:
  test("特徴量を特徴量名つきの事例に変換する") {
    val columns = Vector("花弁長さ", "花弁幅")
    val x = Vector(Features(columns, Vector(0.1, 0.2)), Features(columns, Vector(0.6, 0.8)))

    val dataset = TribuoTrees.toDataset(x, Vector("setosa", "virginica"))

    assert(dataset.size === 2)
    assert(dataset.getFeatureIDMap.asScala.map(_.getName).toSet === Set("花弁長さ", "花弁幅"))
    assert(
      dataset.getOutputInfo.getDomain.asScala.map(_.getLabel).toSet === Set("setosa", "virginica")
    )
  }

  test("分割候補や多数決が同じにならなければ Tribuo の CART と自作の決定木は同じ予測をする") {
    val newX = Samples.column("花弁幅", 0.2, 0.4, 0.55, 0.75, 0.95)

    val tribuo = TribuoTrees.predict(
      TribuoTrees.train(Samples.threeSpeciesX, Samples.threeSpeciesT, TribuoTrees.Unlimited),
      newX
    )

    assert(
      tribuo === DecisionTree
        .unlimited()
        .fit(Samples.threeSpeciesX, Samples.threeSpeciesT)
        .predict(newX)
    )
  }

  test("葉の多数決が同数のとき自作は先に現れたラベルを選ぶが Tribuo は出現順に依存しない") {
    val x = Samples.column("花弁幅", 0.1, 0.1)
    val orders = Vector(Vector("b", "a"), Vector("a", "b"))

    val mine = orders.map(t => DecisionTree.unlimited().fit(x, t).predict(x).head)
    val tribuo =
      orders
        .map(t => TribuoTrees.predict(TribuoTrees.train(x, t, TribuoTrees.Unlimited), x).head)
        .distinct

    assert(mine === Vector("b", "a"))
    assert(tribuo.size === 1)
  }

  test("同じ不純度の分割候補が複数あるとき自作は列の順で選ぶが Tribuo は特徴量名の順で選ぶ") {
    val columns = Vector("b", "a")
    val x = Vector(Features(columns, Vector(0.1, 0.1)), Features(columns, Vector(0.9, 0.9)))
    val t = Vector("left", "right")
    val newX = Vector(Features(columns, Vector(0.2, 0.8)))

    assert(DecisionTree.unlimited().fit(x, t).predict(newX) === Vector("left"))
    assert(
      TribuoTrees.predict(TribuoTrees.train(x, t, TribuoTrees.Unlimited), newX) === Vector("right")
    )
  }
