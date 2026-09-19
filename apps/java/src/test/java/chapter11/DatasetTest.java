package chapter11;

import static org.assertj.core.api.Assertions.assertThat;

import chapter02.Features;
import chapter02.Row;
import chapter02.Table;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DatasetTest {
  private static Table table(List<String> columns, List<List<String>> rows) {
    return new Table(
        columns,
        rows.stream()
            .map(
                values -> {
                  Map<String, String> cells = new HashMap<>();
                  for (int i = 0; i < columns.size(); i++) {
                    cells.put(columns.get(i), values.get(i));
                  }
                  return new Row(cells);
                })
            .toList());
  }

  @Test
  @DisplayName("客室クラスと年齢と男性かどうかを特徴量にし生存を正解ラベルにする")
  void survived() {
    var data =
        Dataset.prepareSurvived(
            table(
                List.of("PassengerId", "Survived", "Pclass", "Sex", "Age", "Fare"),
                List.of(
                    List.of("1", "0", "3", "male", "30", "8"),
                    List.of("2", "1", "1", "female", "40", "60"))));

    assertThat(data.x())
        .containsExactly(
            new Features(List.of("Pclass", "Age", "male"), new double[] {3, 30, 1}),
            new Features(List.of("Pclass", "Age", "male"), new double[] {1, 40, 0}));
    assertThat(data.t()).containsExactly("0", "1");
  }

  @Test
  @DisplayName("年齢の欠損値を年齢の平均値で補完する")
  void survivedFillsAge() {
    var data =
        Dataset.prepareSurvived(
            table(
                List.of("Survived", "Pclass", "Sex", "Age"),
                List.of(
                    List.of("0", "3", "male", "20"),
                    List.of("1", "1", "female", ""),
                    List.of("1", "2", "female", "40"))));

    assertThat(data.x().stream().map(f -> f.value("Age")).toList())
        .containsExactly(20.0, 30.0, 40.0);
  }

  @Test
  @DisplayName("興行収入を正解にし特徴量の欠損値を平均値で補完する")
  void cinema() {
    var data =
        Dataset.prepareCinema(
            table(
                List.of("cinema_id", "SNS1", "SNS2", "actor", "original", "sales"),
                List.of(
                    List.of("101", "100", "500", "", "0", "9000"),
                    List.of("102", "", "600", "20", "1", "9500"),
                    List.of("103", "300", "700", "40", "0", "10000"))));

    List<String> columns = List.of("SNS1", "SNS2", "actor", "original");
    assertThat(data.x())
        .containsExactly(
            new Features(columns, new double[] {100, 500, 30, 0}),
            new Features(columns, new double[] {200, 600, 20, 1}),
            new Features(columns, new double[] {300, 700, 40, 0}));
    assertThat(data.t()).containsExactly(9000.0, 9500.0, 10000.0);
  }
}
