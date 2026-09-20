package machinelearning.chapter02

import java.nio.file.{Files, Paths}
import machinelearning.dataset.DataDir
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class IrisDataSpec extends AnyFunSuite:
  private val csvFile = Paths.get(DataDir.current(), "iris.csv")

  private def requireData(): org.scalatest.Assertion =
    assume(Files.exists(csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）")

  test("実データの列ごとの欠損値の数を数える") {
    requireData(): Unit

    assert(
      Table.load(csvFile).countMissing
        === Vector("がく片長さ" -> 2, "がく片幅" -> 1, "花弁長さ" -> 2, "花弁幅" -> 2, "種類" -> 0)
    )
  }

  test("実データを 105 件と 45 件に分けて欠損値を補完する") {
    requireData(): Unit

    val split = Preprocessing.prepareIris(csvFile, 0.3, 0)

    assert((split.xTrain.size, split.xTest.size) === (105, 45))
    assert((split.tTrain.size, split.tTest.size) === (105, 45))
  }

  test("訓練データの平均値は Java 版と同じになる（同じ乱数と同じ分け方）") {
    requireData(): Unit
    val (columns, rows, target) =
      Preprocessing.splitFeaturesAndTarget(Table.load(csvFile), Preprocessing.Target)
    val split = Preprocessing.splitTrainTest(rows, target, 0.3, 0)

    val means = Preprocessing.columnMeans(split.xTrain, columns)

    // Java 版（java.util.Random と同じ Fisher-Yates）で実測した値
    assert(means("がく片長さ") === 0.4215384615384616 +- 1e-12)
    assert(means("がく片幅") === 0.43826923076923074 +- 1e-12)
    assert(means("花弁長さ") === 0.47644230769230766 +- 1e-12)
    assert(means("花弁幅") === 0.4475 +- 1e-12)
    assert(split.tTest.take(3) === Vector("Iris-setosa", "Iris-virginica", "Iris-setosa"))
  }

  test("実行すると前処理の結果を表示する") {
    requireData(): Unit
    val output = Vector.newBuilder[String]

    Main.run(output += _)

    assert(
      output.result() === Vector(
        "データ件数: 150",
        "欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0",
        "訓練データ: 105 件, テストデータ: 45 件",
        "特徴量: がく片長さ, がく片幅, 花弁長さ, 花弁幅"
      )
    )
  }
