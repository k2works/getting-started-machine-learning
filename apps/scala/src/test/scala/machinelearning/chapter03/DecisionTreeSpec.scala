package machinelearning.chapter03

import machinelearning.chapter02.Features
import machinelearning.chapter03.Tree.{Leaf, Node}
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

/** テスト用の特徴量を作る。 */
object Samples:
  /** 1 列だけの特徴量を値の数だけ作る。 */
  def column(name: String, values: Double*): Vector[Features] =
    values.toVector.map(value => Features(Vector(name), Vector(value)))

  /** 3 品種のうち、境界の右側に versicolor 2 件と virginica 1 件が残るデータ。 */
  val threeSpeciesX: Vector[Features] = column("花弁幅", 0.1, 0.2, 0.3, 0.5, 0.6, 0.9)
  val threeSpeciesT: Vector[String] =
    Vector("setosa", "setosa", "setosa", "versicolor", "versicolor", "virginica")

class DecisionTreeSpec extends AnyFunSuite:
  test("1 種類のラベルだけならジニ不純度は 0") {
    assert(DecisionTrees.gini(Vector("setosa", "setosa", "setosa")) === 0.0)
  }

  test("2 種類のラベルが半分ずつならジニ不純度は 0.5") {
    assert(DecisionTrees.gini(Vector("setosa", "virginica")) === 0.5)
  }

  test("3 種類のラベルが同じ数ならジニ不純度は 3 分の 2") {
    assert(DecisionTrees.gini(Vector("setosa", "versicolor", "virginica")) === 2.0 / 3 +- 1e-12)
  }

  test("ラベルを完全に分けられる境界を見つける") {
    val split = DecisionTrees
      .bestSplit(
        Samples.column("花弁幅", 0.1, 0.2, 0.7, 0.8),
        Vector("setosa", "setosa", "virginica", "virginica")
      )
      .get

    assert(split.feature === "花弁幅")
    assert(split.threshold === 0.45 +- 1e-12)
    assert(split.impurity === 0.0 +- 1e-12)
  }

  test("複数の特徴量から不純度が最も小さくなる特徴量と境界を選ぶ") {
    val columns = Vector("がく片長さ", "花弁長さ")
    val x = Vector(
      Features(columns, Vector(0.1, 0.2)),
      Features(columns, Vector(0.3, 0.1)),
      Features(columns, Vector(0.2, 0.9)),
      Features(columns, Vector(0.4, 0.6))
    )

    val split = DecisionTrees.bestSplit(x, Vector("setosa", "setosa", "virginica", "virginica")).get

    assert(split.feature === "花弁長さ")
    assert(split.threshold === 0.4 +- 1e-12)
  }

  test("ラベルが 1 種類なら分割しない") {
    assert(
      DecisionTrees.bestSplit(
        Samples.column("花弁幅", 0.1, 0.2, 0.7),
        Vector("setosa", "setosa", "setosa")
      ) === None
    )
  }

  test("1 種類のラベルだけを学習するとそのラベルを予測する") {
    val model =
      DecisionTree.unlimited().fit(Samples.column("花弁幅", 0.1, 0.2), Vector("setosa", "setosa"))

    assert(model.predict(Samples.column("花弁幅", 0.15, 0.9)) === Vector("setosa", "setosa"))
  }

  test("境界の左右で異なるラベルを予測する") {
    val model = DecisionTree
      .unlimited()
      .fit(
        Samples.column("花弁幅", 0.1, 0.2, 0.7, 0.8),
        Vector("setosa", "setosa", "virginica", "virginica")
      )

    assert(model.predict(Samples.column("花弁幅", 0.15, 0.75)) === Vector("setosa", "virginica"))
  }

  test("深さを制限しなければすべての訓練データを分け切る") {
    val model = DecisionTree.unlimited().fit(Samples.threeSpeciesX, Samples.threeSpeciesT)

    assert(model.predict(Samples.threeSpeciesX) === Samples.threeSpeciesT)
  }

  test("深さを 1 に制限すると境界の先は多数派のラベルを予測する") {
    val model = DecisionTree.withMaxDepth(1).fit(Samples.threeSpeciesX, Samples.threeSpeciesT)

    assert(model.predict(Samples.column("花弁幅", 0.2, 0.95)) === Vector("setosa", "versicolor"))
  }

  test("学習する前に予測するとエラーになる") {
    val error =
      intercept[IllegalStateException](DecisionTree.unlimited().predict(Samples.column("花弁幅", 0.1)))

    assert(error.getMessage === "fit で学習してから predict を呼んでください")
  }

  test("葉だけの木はラベルを表示する") {
    assert(DecisionTrees.format(Leaf("setosa")) === "setosa")
  }

  test("節は条件ごとに字下げして表示する") {
    val tree = Node(
      Split("花弁幅", 0.4, 0.0),
      Leaf("setosa"),
      Node(Split("花弁長さ", 0.75, 0.0), Leaf("versicolor"), Leaf("virginica"))
    )

    assert(
      DecisionTrees.format(tree) === Vector(
        "花弁幅 <= 0.4000",
        "  setosa",
        "花弁幅 > 0.4000",
        "  花弁長さ <= 0.7500",
        "    versicolor",
        "  花弁長さ > 0.7500",
        "    virginica"
      ).mkString("\n")
    )
  }
