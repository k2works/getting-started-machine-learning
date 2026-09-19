package chapter01;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import support.StdoutCapture;

class KvsTDataTest {
  private final Path csvFile = DataDir.dataDir().resolve("KvsT.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(Files.exists(csvFile), "学習データ KvsT.csv が配置されていない（gulp data:setup）");
  }

  @Test
  @DisplayName("実データから 19 人分を読み込む")
  void loadsNineteenPeople() throws IOException {
    assertThat(KinokoTakenoko.loadPeople(csvFile)).hasSize(19);
  }

  @Test
  @DisplayName("ルールによる判定の正解率を実データで計算する")
  void accuracyOfRule() throws IOException {
    FeaturesAndLabels split =
        KinokoTakenoko.splitFeaturesAndLabels(KinokoTakenoko.loadPeople(csvFile));

    var predictions = split.features().stream().map(KinokoTakenoko::predictByRule).toList();

    assertThat(KinokoTakenoko.accuracy(predictions, split.labels()))
        .isCloseTo(14.0 / 19, within(1e-12));
  }

  @Test
  @DisplayName("実行するとデータ件数と正解率を表示する")
  void mainPrintsSummary() throws Exception {
    String output = StdoutCapture.capture(() -> Main.main(new String[0]));

    assertThat(output).isEqualTo("データ件数: 19\nルールによる判定の正解率: 0.7368\n");
  }
}
