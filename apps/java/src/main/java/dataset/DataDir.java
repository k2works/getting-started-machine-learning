package dataset;

import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Function;

/** 学習データのディレクトリを求める。 */
public final class DataDir {
  private DataDir() {}

  /** 環境変数 ML_DATA_DIR が無ければ apps/data/sukkiri-ml を使う。 */
  public static Path dataDir(Function<String, String> getenv) {
    return Optional.ofNullable(getenv.apply("ML_DATA_DIR"))
        .map(Path::of)
        .orElse(Path.of("../data/sukkiri-ml"));
  }

  /** 実行中のプロセスの環境変数から学習データのディレクトリを求める。 */
  public static Path dataDir() {
    return dataDir(System::getenv);
  }
}
