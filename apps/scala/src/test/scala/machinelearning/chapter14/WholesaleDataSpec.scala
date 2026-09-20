package machinelearning.chapter14

import java.nio.file.{Files, Paths}
import machinelearning.dataset.DataDir
import org.scalactic.Tolerance.*
import org.scalatest.funsuite.AnyFunSuite

class WholesaleDataSpec extends AnyFunSuite:
  private val csvFile = Paths.get(DataDir.current(), "Wholesale.csv")

  private def requireData(): org.scalatest.Assertion =
    assume(Files.exists(csvFile), "学習データ Wholesale.csv が配置されていない（gulp data:setup）")

  test("実データから 440 件の支出額 6 列を読み込む") {
    requireData(): Unit
    val x = Spending.load(csvFile)

    assert(x.size === 440)
    assert(x.head.columns.size === 6)
  }

  test("標準化したデータのクラスタ数 1 の SSE は件数と列数の積になる") {
    requireData(): Unit
    val points = Spending.standardize(Spending.load(csvFile))

    val sse = KMeans.sseByClusterCount(points, Vector(1), 0)(1)

    assert(sse === 440.0 * 6 +- 1e-6)
  }

  test("クラスタ数を増やすほど SSE が小さくなる") {
    requireData(): Unit
    val points = Spending.standardize(Spending.load(csvFile))

    val sse = KMeans.sseByClusterCount(points, (1 to 10).toVector, 0).values.toVector

    assert(sse.sliding(2).forall(pair => pair(1) < pair(0)))
  }

  test("実行すると SSE とクラスタごとの件数と平均支出額を表示する") {
    requireData(): Unit
    val output = Vector.newBuilder[String]

    Main.run(output += _)

    assert(
      output.result() === Vector(
        "データ件数: 440（支出額 6 列）",
        "クラスタ数ごとの SSE（初期中心 10 通りの最小値）:",
        "クラスタ数\t自作\tTribuo（k-means++）",
        "1\t2640.00\t2640.00",
        "2\t1954.18\t1954.78",
        "3\t1614.52\t1607.67",
        "4\t1334.36\t1317.90",
        "5\t1085.27\t1058.77",
        "6\t947.20\t917.67",
        "7\t888.22\t839.38",
        "8\t775.24\t742.02",
        "9\t690.81\t655.14",
        "10\t618.17\t606.81",
        "",
        "クラスタ数 5 のクラスタごとの件数と平均支出額:",
        "クラスタ\t件数\tFresh\tMilk\tGrocery\tFrozen\tDetergents_Paper\tDelicassen",
        "2\t265\t8909\t2967\t3804\t2248\t989\t962",
        "1\t96\t5509\t10556\t16478\t1420\t7199\t1659",
        "0\t65\t31117\t4260\t5374\t7225\t849\t2286",
        "3\t10\t15965\t34709\t48537\t3055\t24875\t2943",
        "4\t4\t52022\t31696\t18491\t29826\t2699\t19656"
      )
    )
  }
