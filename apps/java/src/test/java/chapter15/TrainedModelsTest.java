package chapter15;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import dataset.DataDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import support.StdoutCapture;

class TrainedModelsTest {
  @TempDir Path modelDir;

  @BeforeEach
  void requireData() {
    Path data = DataDir.dataDir();
    assumeTrue(
        Files.exists(data.resolve("cinema.csv")) && Files.exists(data.resolve("Survived.csv")),
        "学習データ cinema.csv・Survived.csv が配置されていない（gulp data:setup）");
  }

  private FileModelStore trainedStore() throws Exception {
    var store = new FileModelStore(modelDir);
    Training.trainAndSaveModels(DataDir.dataDir(), store);
    return store;
  }

  @Test
  @DisplayName("学習したモデルを保存するとヘルスチェックが ok になる")
  void healthy() throws Exception {
    assertThat(new PredictionService(trainedStore()).health())
        .containsExactly(Map.entry("cinema", true), Map.entry("survived", true));
  }

  @Test
  @DisplayName("学習した線形回帰モデルで興行収入を予測する")
  void predictsSales() throws Exception {
    double sales = trainedStore().loadSalesModel().predictSales(new Movie(200.0, 500.0, 3000.0, 1));

    assertThat(sales).isPositive();
  }

  @Test
  @DisplayName("学習したパイプラインで 1 等客室の女性は生存と予測する")
  void firstClassWoman() throws Exception {
    var passenger =
        new Passenger(1, "female", OptionalDouble.of(30.0), 0, 0, 80.0, Optional.of("C"));

    assertThat(trainedStore().loadSurvivalModel().survives(passenger)).isTrue();
  }

  @Test
  @DisplayName("学習したパイプラインで 3 等客室の男性は死亡と予測する")
  void thirdClassMan() throws Exception {
    var passenger = new Passenger(3, "male", OptionalDouble.of(30.0), 0, 0, 8.0, Optional.of("S"));

    assertThat(trainedStore().loadSurvivalModel().survives(passenger)).isFalse();
  }

  @Test
  @DisplayName("学習するとモデルを保存して起動する URL を表示する")
  void reports() throws Exception {
    String output = StdoutCapture.capture(() -> Main.trainAndReport(modelDir));

    assertThat(output)
        .isEqualTo(
            """
            学習済みモデルを保存しました: cinema.json, survived.ser
            API を起動します: http://127.0.0.1:8015
            """);
    assertThat(modelDir.resolve("cinema.json")).exists();
    assertThat(modelDir.resolve("survived.ser")).exists();
  }
}
