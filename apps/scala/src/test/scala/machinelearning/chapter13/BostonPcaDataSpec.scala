package machinelearning.chapter13

import java.nio.file.{Files, Paths}
import machinelearning.chapter07.Matrix
import machinelearning.dataset.DataDir
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class BostonPcaDataSpec extends AnyFunSuite:
  private val csvFile = Paths.get(DataDir.current(), "Boston.csv")

  private def requireData(): org.scalatest.Assertion =
    assume(Files.exists(csvFile), "学習データ Boston.csv が配置されていない（gulp data:setup）")

  test("CRIME をダミー変数にして 15 列の標準化済みデータにする") {
    requireData(): Unit
    val x = BostonPca.load(csvFile)

    assert(x.size === 100)
    assert(x.head.columns.size === 15)
  }

  test("実データの主成分も分散共分散行列の固有ベクトルになる") {
    requireData(): Unit
    val x = BostonPca.toMatrix(BostonPca.load(csvFile))

    val model = Pca.fit(x, 15)

    val covariance = Pca.covarianceMatrix(x)
    model.components.rows.zip(model.explainedVariance).foreach { (component, variance) =>
      val projected = covariance * Matrix.columnVector(component*)
      projected.column(0).zip(component).foreach { (actual, value) =>
        assert(actual === value * variance +- 1e-9)
      }
    }
    assert(model.explainedVarianceRatio.sum === 1.0 +- 1e-9)
  }

  test("実行すると寄与率と主成分の解釈を表示する") {
    requireData(): Unit
    val output = Vector.newBuilder[String]

    Main.run(output += _)

    assert(
      output.result() === Vector(
        "データ件数: 100, 列数: 15",
        "寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581",
        "累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）",
        "第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328",
        "第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405"
      )
    )
  }
