package dataset;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DataDirTest {
  @Test
  @DisplayName("環境変数 ML_DATA_DIR が指定されていればそのディレクトリを返す")
  void usesEnvironmentVariable() {
    Map<String, String> env = Map.of("ML_DATA_DIR", "/tmp/ml-data");

    assertThat(DataDir.dataDir(env::get)).isEqualTo(Path.of("/tmp/ml-data"));
  }

  @Test
  @DisplayName("環境変数が無ければ apps の data ディレクトリを返す")
  void defaultsToAppsData() {
    assertThat(DataDir.dataDir(name -> null)).isEqualTo(Path.of("../data/sukkiri-ml"));
  }
}
