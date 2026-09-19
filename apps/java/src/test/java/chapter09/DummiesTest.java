package chapter09;

import static org.assertj.core.api.Assertions.assertThat;

import chapter02.Row;
import chapter02.Table;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DummiesTest {
  private static Table crimeTable(String... crimes) {
    List<Row> rows =
        List.of(crimes).stream().map(c -> new Row(Map.of("CRIME", c, "RM", "6.0"))).toList();
    return new Table(List.of("CRIME", "RM"), rows);
  }

  @Test
  @DisplayName("先頭を除いたカテゴリを辞書順に返す")
  void categoriesWithoutFirst() {
    assertThat(Dummies.categories(List.of("low", "high", "very_low", "low")))
        .containsExactly("low", "very_low");
  }

  @Test
  @DisplayName("欠損値（空欄）はカテゴリに数えない")
  void categoriesIgnoreMissing() {
    assertThat(Dummies.categories(List.of("low", "", "high"))).containsExactly("low");
  }

  @Test
  @DisplayName("カテゴリごとに 0 と 1 の列を作り元の列を取り除く")
  void encodesDummies() {
    Table encoded =
        Dummies.encode(crimeTable("low", "high", "very_low"), "CRIME", List.of("low", "very_low"));

    assertThat(encoded.columns()).containsExactly("RM", "CRIME_low", "CRIME_very_low");
    assertThat(encoded.rows()).extracting(r -> r.text("RM")).containsExactly("6.0", "6.0", "6.0");
    assertThat(encoded.rows()).extracting(r -> r.text("CRIME_low")).containsExactly("1", "0", "0");
    assertThat(encoded.rows())
        .extracting(r -> r.text("CRIME_very_low"))
        .containsExactly("0", "0", "1");
  }

  @Test
  @DisplayName("カテゴリに無い値はすべての列が 0 になる")
  void unknownCategoryIsAllZero() {
    Table encoded = Dummies.encode(crimeTable("unknown"), "CRIME", List.of("low", "very_low"));

    assertThat(encoded.rows().getFirst().text("CRIME_low")).isEqualTo("0");
    assertThat(encoded.rows().getFirst().text("CRIME_very_low")).isEqualTo("0");
  }
}
