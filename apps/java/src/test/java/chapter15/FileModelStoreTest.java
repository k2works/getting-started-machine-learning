package chapter15;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import chapter02.Row;
import chapter02.Table;
import chapter07.LinearModel;
import chapter08.ClassWeight;
import chapter08.Pipeline;
import chapter08.SurvivedData;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileModelStoreTest {
  @TempDir Path directory;

  private static Passenger passenger(String sex) {
    return new Passenger(2, sex, OptionalDouble.empty(), 0, 0, 20.0, Optional.empty());
  }

  /** 女性は生存、男性は死亡という架空の乗客 8 人。 */
  private static Table fictionalPassengers() {
    String[][] values = {
      {"1", "female", "25", "0", "0", "60", "C"},
      {"2", "female", "35", "1", "0", "30", "S"},
      {"3", "female", "18", "0", "1", "10", "Q"},
      {"3", "female", "", "0", "0", "9", "S"},
      {"1", "male", "40", "1", "0", "55", "C"},
      {"2", "male", "28", "0", "0", "15", "S"},
      {"3", "male", "22", "0", "0", "8", ""},
      {"3", "male", "", "0", "0", "7", "S"},
    };
    List<Row> rows = new ArrayList<>();
    for (String[] row : values) {
      Map<String, String> cells = new HashMap<>();
      for (int i = 0; i < row.length; i++) {
        cells.put(SurvivedData.FEATURES.get(i), row[i]);
      }
      rows.add(new Row(cells));
    }
    return new Table(SurvivedData.FEATURES, rows);
  }

  @Test
  @DisplayName("保存した線形回帰モデルを読み込んで興行収入を予測する")
  void salesModel() throws Exception {
    var store = new FileModelStore(directory);
    store.saveSalesModel(
        new LinearModel(100.0, Map.of("SNS1", 1.0, "SNS2", 2.0, "actor", 0.5, "original", 10.0)));

    SalesModel model = store.loadSalesModel();

    assertThat(model.predictSales(new Movie(10.0, 20.0, 100.0, 1))).isCloseTo(210.0, within(1e-9));
  }

  @Test
  @DisplayName("保存したパイプラインを読み込んで生存を判定する")
  void survivalModel() throws Exception {
    var store = new FileModelStore(directory);
    store.saveSurvivalModel(
        Pipeline.build(2, ClassWeight.NONE)
            .fit(fictionalPassengers(), List.of(1, 1, 1, 1, 0, 0, 0, 0)));

    SurvivalModel model = store.loadSurvivalModel();

    assertThat(model.survives(passenger("female"))).isTrue();
    assertThat(model.survives(passenger("male"))).isFalse();
  }

  @Test
  @DisplayName("モデルファイルが無ければ、パスを含まない ModelNotFoundException を投げる")
  void missingFiles() {
    var store = new FileModelStore(directory);

    assertThatThrownBy(store::loadSalesModel)
        .isInstanceOf(ModelNotFoundException.class)
        .hasMessageNotContaining(directory.toString());
    assertThatThrownBy(store::loadSurvivalModel)
        .isInstanceOfSatisfying(
            ModelNotFoundException.class, e -> assertThat(e.model()).isEqualTo("survived"));
  }
}
