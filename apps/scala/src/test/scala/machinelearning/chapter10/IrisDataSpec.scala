package machinelearning.chapter10

import java.nio.file.{Files, Paths}
import machinelearning.dataset.DataDir
import org.scalatest.funsuite.AnyFunSuite

class IrisDataSpec extends AnyFunSuite:
  private val csvFile = Paths.get(DataDir.current(), "iris.csv")

  private def requireData(): org.scalatest.Assertion =
    assume(Files.exists(csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）")

  test("実行するとモデルごとの正解率と特徴量の重要度を表示する") {
    requireData(): Unit
    val output = Vector.newBuilder[String]

    Main.run(output += _)

    // 自作のモデルの数値は Java 版と一致する（分割も乱数も同じ手順のため）
    assert(
      output.result() === Vector(
        "モデル\t訓練データ\tテストデータ",
        "決定木（深さ 2）\t0.9333\t0.9556",
        "ロジスティック回帰\t0.9143\t0.9111",
        "ランダムフォレスト（100 本）\t1.0000\t0.9333",
        "ランダムフォレスト（100 本・深さ 2）\t0.9429\t0.9556",
        "",
        "ランダムフォレスト（100 本）の特徴量の重要度:",
        "がく片長さ\t0.1882",
        "がく片幅\t0.1271",
        "花弁長さ\t0.2708",
        "花弁幅\t0.4140"
      )
    )
  }
