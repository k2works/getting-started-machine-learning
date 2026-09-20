package machinelearning.chapter01

import java.nio.file.{Files, Paths}
import machinelearning.dataset.DataDir
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class KvsTDataSpec extends AnyFunSuite:
  private val csvFile = Paths.get(DataDir.current(), "KvsT.csv")

  /** 学習データが無ければテストを飛ばす。assume の戻り値は捨てられないので（-Wvalue-discard）そのまま返す。 */
  private def requireData(): org.scalatest.Assertion =
    assume(Files.exists(csvFile), "学習データ KvsT.csv が配置されていない（gulp data:setup）")

  test("実データから 19 人分を読み込む") {
    requireData(): Unit

    assert(KinokoTakenoko.loadPeople(csvFile).size === 19)
  }

  test("ルールによる判定の正解率を実データで計算する") {
    requireData(): Unit
    val (features, labels) =
      KinokoTakenoko.splitFeaturesAndLabels(KinokoTakenoko.loadPeople(csvFile))

    val predictions = features.map(KinokoTakenoko.predictByRule)

    assert(KinokoTakenoko.accuracy(predictions, labels) === 14.0 / 19 +- 1e-12)
  }

  test("実行するとデータ件数と正解率を表示する") {
    requireData(): Unit
    val output = Vector.newBuilder[String]

    Main.run(output += _)

    assert(output.result() === Vector("データ件数: 19", "ルールによる判定の正解率: 0.7368"))
  }
