package machinelearning.chapter09

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import org.scalatest.funsuite.AnyFunSuite

class BostonSpec extends AnyFunSuite:
  test("ダミー変数化と欠損値の補完をして特徴量と価格に分ける") {
    val csvFile = Files.createTempDirectory("chapter09").resolve("boston.csv")
    val _ = Files.writeString(
      csvFile,
      """CRIME,RM,NOX,PRICE
        |low,6.0,,20.0
        |high,5.0,0.5,15.0
        |very_low,7.0,0.4,30.0
        |low,6.5,0.6,25.0
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    val split = Boston.prepare(csvFile, 0.5, 0)

    assert(split.xTrain.head.columns === Vector("RM", "NOX", "CRIME_low", "CRIME_very_low"))
    assert(split.xTrain.size === 2)
    assert(split.xTest.size === 2)
    assert(split.tTrain.size === 2)
    assert(split.tTest.size === 2)
  }
