package machinelearning.chapter12

import java.nio.file.{Files, Paths}
import machinelearning.dataset.DataDir
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class BostonDataSpec extends AnyFunSuite:
  private val csvFile = Paths.get(DataDir.current(), "Boston.csv")

  private def requireData(): org.scalatest.Assertion =
    assume(Files.exists(csvFile), "学習データ Boston.csv が配置されていない（gulp data:setup）")

  private def dataset(): BostonDataset = Boston.prepare(csvFile, 0.3, 0.3, 0)

  test("外れ値を除いて訓練データと検証データとテストデータに分ける") {
    requireData(): Unit

    val data = dataset()

    assert((data.tTrain.size, data.tValid.size, data.tTest.size) === (47, 21, 30))
    assert(data.featureNames.size === 9)
    assert(data.xTrain.columnCount === 9)
  }

  test("実データでも自作のリッジ回帰は Tribuo の ElasticNetCDTrainer と同じ係数と切片になる") {
    requireData(): Unit
    val data = dataset()

    val model = Ridge.fit(data.xTrain, data.tTrain, 10.0)
    val tribuo = TribuoRegularization.fitRidge(data.xTrain, data.tTrain, 10.0)

    model.coefficients.lazyZip(tribuo.coefficients).foreach { (a, b) =>
      assert(b === a +- 1e-6)
    }
    assert(tribuo.intercept === model.intercept +- 1e-6)
  }

  test("実行すると正則化の実験結果を表示する") {
    requireData(): Unit
    val output = Vector.newBuilder[String]

    Main.run(output += _)

    assert(
      output.result() === Vector(
        "データ件数: 98（外れ値 2 件を除外）",
        "訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件",
        "特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2",
        "alpha  訓練 R²  検証 R²  係数の絶対値の合計",
        "  0.0  0.8827  0.7272  14.187",
        "  0.1  0.8827  0.7274  14.104",
        "  1.0  0.8823  0.7288  13.594",
        " 10.0  0.8681  0.7349  11.573",
        "100.0  0.6583  0.5985  5.684",
        "検証データで選んだ alpha: 10.0",
        "テストデータの決定係数: 線形回帰 0.5224, リッジ回帰 0.6243",
        "ラッソ回帰（alpha=0.5）で係数が 0 になった特徴量: PTRATIO^2, PTRATIO LSTAT, LSTAT^2"
      )
    )
  }
