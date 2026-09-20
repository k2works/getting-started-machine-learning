package machinelearning.dataset

import org.scalatest.funsuite.AnyFunSuite

class DataDirSpec extends AnyFunSuite:
  test("環境変数 ML_DATA_DIR が指定されていればそのディレクトリを返す") {
    val env = Map("ML_DATA_DIR" -> "/tmp/ml-data")

    assert(DataDir.from(env.get) === "/tmp/ml-data")
  }

  test("環境変数が無ければ apps/data/sukkiri-ml を返す") {
    assert(DataDir.from(_ => None).endsWith("data/sukkiri-ml"))
  }
