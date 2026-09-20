package machinelearning.chapter09

import java.nio.file.{Files, Path, Paths}
import machinelearning.chapter02.{Features, TrainTestSplit}
import machinelearning.dataset.DataDir
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class FeatureEngineeringDataSpec extends AnyFunSuite:
  private val bostonCsv = Paths.get(DataDir.current(), "Boston.csv")
  private val bikeTsv = Paths.get(DataDir.current(), "bike.tsv")
  private val weatherCsv = Paths.get(DataDir.current(), "weather.csv")

  private def requireData(): org.scalatest.Assertion =
    assume(
      Vector(bostonCsv, bikeTsv, weatherCsv).forall(Files.exists(_: Path)),
      "学習データ Boston.csv・bike.tsv・weather.csv が配置されていない（gulp data:setup）"
    )

  private def bostonSplit(): TrainTestSplit[Features, Double] = Boston.prepare(bostonCsv, 0.3, 0)

  test("実データを 70 件と 30 件に分けて欠損値を補完する") {
    requireData(): Unit
    val split = bostonSplit()

    assert(split.xTrain.size === 70)
    assert(split.xTest.size === 30)
  }

  test("2 乗の項を加えるとテストデータの決定係数が上がる") {
    requireData(): Unit
    val split = bostonSplit()

    val base = Boston.scoreFeatureSet(split, Main.Columns, Main.Columns)
    val squares = Boston.scoreFeatureSet(split, Main.Columns, Main.Columns ++ Main.Squares)

    assert(base.test === 0.6950 +- 1e-4)
    assert(squares.test === 0.8628 +- 1e-4)
  }

  test("weather.csv は Shift_JIS で、bike.tsv と結合すると 731 行になる") {
    requireData(): Unit

    val joined =
      BikeWeather.joinWeather(BikeWeather.loadBike(bikeTsv), BikeWeather.loadWeather(weatherCsv))

    assert(joined.rows.size === 731)
    assert(BikeWeather.meanCountByWeather(joined).map(_._1) === Vector("晴れ", "曇り", "雨"))
  }

  test("実行すると特徴量エンジニアリングの結果を表示する") {
    requireData(): Unit
    val output = Vector.newBuilder[String]

    Main.run(output += _)

    assert(
      output.result() === Vector(
        "訓練データ: 70 件, テストデータ: 30 件",
        "特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, " +
          "CRIME_low, CRIME_very_low",
        "標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00",
        "決定係数:",
        "  元の特徴量（3 列）: 訓練 0.6056, テスト 0.6950",
        "  2 乗の項を追加（6 列）: 訓練 0.7740, テスト 0.8628",
        "  交互作用の項も追加（9 列）: 訓練 0.7953, テスト 0.8213",
        "訓練データの PRICE の外れ値: 8 件",
        "  外れ値を除いて 2 乗の項を追加: 訓練 0.6717, テスト 0.7947",
        "天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3"
      )
    )
  }
