package machinelearning.dataset

import java.nio.file.Paths

/** 学習データのディレクトリを求める。 */
object DataDir:

  /** 環境変数 ML_DATA_DIR が無ければ apps/data/sukkiri-ml を使う。
    *
    * @param getenv
    *   環境変数を読む関数。テストでは差し替える
    */
  def from(getenv: String => Option[String]): String =
    getenv("ML_DATA_DIR").getOrElse(Paths.get("..", "data", "sukkiri-ml").toString)

  /** 実行中のプロセスの環境変数から学習データのディレクトリを求める。 */
  def current(): String = from(sys.env.get)
