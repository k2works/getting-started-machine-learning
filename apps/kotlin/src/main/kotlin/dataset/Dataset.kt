package dataset

import java.io.File

/** 学習データのディレクトリ。環境変数 ML_DATA_DIR が無ければ apps/data/sukkiri-ml を使う。 */
fun dataDir(getenv: (String) -> String? = System::getenv): File = getenv("ML_DATA_DIR")?.let(::File) ?: File("../data/sukkiri-ml")
