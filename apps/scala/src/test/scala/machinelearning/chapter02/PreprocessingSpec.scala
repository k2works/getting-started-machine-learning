package machinelearning.chapter02

import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class PreprocessingSpec extends AnyFunSuite:
  private def sample(sepalLength: String, sepalWidth: String): Row =
    Row(Map("がく片長さ" -> sepalLength, "がく片幅" -> sepalWidth))

  private val numbers = (0 until 10).toVector
  private val labels = numbers.map(i => s"label$i")

  test("列名と値の数が違えばエラーになる") {
    assertThrows[IllegalArgumentException](Features(Vector("a", "b"), Vector(0.1)))
  }

  test("列名と値が同じなら等しい（Vector は値で比べられる）") {
    assert(Features(Vector("a"), Vector(0.1)) === Features(Vector("a"), Vector(0.1)))
  }

  test("欠損値を除いて列ごとの平均値を求める") {
    val rows = Vector(sample("0.1", "0.2"), sample("", "0.4"), sample("0.3", "0.9"))

    val means = Preprocessing.columnMeans(rows, Vector("がく片長さ", "がく片幅"))

    assert(means("がく片長さ") === 0.2 +- 1e-12)
    assert(means("がく片幅") === 0.5 +- 1e-12)
  }

  test("欠損値を列ごとに指定した値で補完して特徴量にする") {
    val rows = Vector(sample("0.1", ""), sample("", "0.4"))
    val columns = Vector("がく片長さ", "がく片幅")

    val filled = Preprocessing.fillMissing(rows, columns, Map("がく片長さ" -> 0.2, "がく片幅" -> 0.5))

    assert(
      filled === Vector(Features(columns, Vector(0.1, 0.5)), Features(columns, Vector(0.2, 0.4)))
    )
  }

  test("元の行は変更しない") {
    val rows = Vector(sample("", "0.2"))

    val _ = Preprocessing.fillMissing(rows, Vector("がく片長さ", "がく片幅"), Map("がく片長さ" -> 0.2))

    assert(rows.head.isMissing("がく片長さ"))
  }

  test("特徴量の列と正解ラベルの列に分ける") {
    val table = Table(
      Vector("がく片長さ", "花弁幅", "種類"),
      Vector(
        Row(Map("がく片長さ" -> "0.1", "花弁幅" -> "0.4", "種類" -> "Iris-setosa")),
        Row(Map("がく片長さ" -> "0.5", "花弁幅" -> "0.8", "種類" -> "Iris-virginica"))
      )
    )

    val (columns, rows, target) = Preprocessing.splitFeaturesAndTarget(table, "種類")

    assert(columns === Vector("がく片長さ", "花弁幅"))
    assert(rows === table.rows)
    assert(target === Vector("Iris-setosa", "Iris-virginica"))
  }

  test("テストデータの割合どおりの件数に分ける") {
    val split = Preprocessing.splitTrainTest(numbers, labels, 0.3, 0)

    assert((split.xTrain.size, split.xTest.size) === (7, 3))
    assert((split.tTrain.size, split.tTest.size) === (7, 3))
  }

  test("件数が変わってもテストデータの割合どおりに分ける") {
    val twenty = (0 until 20).toVector

    val split = Preprocessing.splitTrainTest(twenty, twenty, 0.25, 0)

    assert((split.xTrain.size, split.xTest.size) === (15, 5))
  }

  test("すべての行を重複なく訓練データとテストデータのどちらかに入れる") {
    val split = Preprocessing.splitTrainTest(numbers, labels, 0.3, 0)

    assert(split.xTrain.intersect(split.xTest).isEmpty)
    assert((split.xTrain ++ split.xTest).sorted === numbers)
  }

  test("特徴量と正解ラベルの対応を保ったまま分ける") {
    val split = Preprocessing.splitTrainTest(numbers, labels, 0.3, 0)

    assert(split.tTrain === split.xTrain.map(i => s"label$i"))
    assert(split.tTest === split.xTest.map(i => s"label$i"))
  }

  test("同じシードなら同じ分け方になる") {
    assert(
      Preprocessing.splitTrainTest(numbers, labels, 0.3, 42).tTest
        === Preprocessing.splitTrainTest(numbers, labels, 0.3, 42).tTest
    )
  }

  test("シードが違えば違う分け方になる") {
    assert(
      Preprocessing.splitTrainTest(numbers, labels, 0.3, 0).tTest
        !== Preprocessing.splitTrainTest(numbers, labels, 0.3, 1).tTest
    )
  }

  test("数値の正解ラベルも特徴量との対応を保ったまま分ける") {
    val numeric = numbers.map(_ * 0.5)

    val split = Preprocessing.splitTrainTest(numbers, numeric, 0.3, 0)

    assert(split.tTest === split.xTest.map(_ * 0.5))
  }

  test("Java 版と同じ Fisher-Yates の並べ替えになる") {
    // Java 版（Collections.shuffle(positions, new Random(0))）で実測した並び
    assert(Preprocessing.shuffle(numbers, 0) === Vector(4, 8, 9, 6, 3, 5, 2, 1, 7, 0))
  }
