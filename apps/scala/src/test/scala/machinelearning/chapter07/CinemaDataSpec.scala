package machinelearning.chapter07

import java.nio.file.{Files, Paths}
import machinelearning.chapter02.Table
import machinelearning.dataset.DataDir
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite
import org.tribuo.regression.Regressor
import org.tribuo.Trainer
import org.tribuo.regression.slm.{LARSTrainer, SLMTrainer}

class CinemaDataSpec extends AnyFunSuite:
  private val csvFile = Paths.get(DataDir.current(), "cinema.csv")

  private def requireData(): org.scalatest.Assertion =
    assume(Files.exists(csvFile), "学習データ cinema.csv が配置されていない（gulp data:setup）")

  test("実データから外れ値を 1 件取り除く") {
    requireData(): Unit
    val table = Table.load(csvFile)

    assert((table.rows.size, Cinema.removeOutliers(table).rows.size) === (100, 99))
  }

  test("実データで自作のモデルと Tribuo の SLMTrainer・LARSTrainer の R² が一致する") {
    requireData(): Unit
    val split = Cinema.prepare(csvFile, 0.2, 0)
    val mine = RegressionMetrics.r2Score(
      split.tTest,
      LinearRegression.fit(split.xTrain, split.tTrain).predict(split.xTest)
    )

    Vector[Trainer[Regressor]](SLMTrainer(true), LARSTrainer()).foreach { trainer =>
      val model = TribuoRegression.train(trainer, split.xTrain, split.tTrain)
      val tribuo =
        RegressionMetrics.r2Score(split.tTest, TribuoRegression.predict(model, split.xTest))
      assert(tribuo === mine +- 1e-9, trainer.getClass.getSimpleName)
    }
  }

  test("実行すると学習した係数と評価指標を表示する") {
    requireData(): Unit
    val output = Vector.newBuilder[String]

    Main.run(output += _)

    assert(
      output.result() === Vector(
        "データ件数: 100",
        "外れ値を除いた件数: 99",
        "訓練データ: 79 件, テストデータ: 20 件",
        "切片: 6114.60",
        "係数: SNS1=1.3804, SNS2=0.5218, actor=0.2900, original=208.8827",
        "テストデータの評価: R2=0.6184, MAE=302.20, RMSE=376.14"
      )
    )
  }
