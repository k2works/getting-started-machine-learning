package chapter01;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoadPeopleTest {
  private static final String HEADER = "\uFEFF身長,体重,年代,派閥\n";

  @TempDir Path directory;

  private Path writeCsv(String rows) throws IOException {
    return Files.writeString(directory.resolve("kvst.csv"), HEADER + rows, StandardCharsets.UTF_8);
  }

  @Test
  @DisplayName("BOM 付き CSV を読み込んで人物のリストを返す")
  void readsCsvWithBom() throws IOException {
    Path csvFile = writeCsv("165,58,30,きのこ\n");

    var people = KinokoTakenoko.loadPeople(csvFile);

    assertThat(people).containsExactly(new Person(165, 58, 30, "きのこ"));
  }

  @Test
  @DisplayName("複数行の CSV を読み込んで行の順に人物のリストを返す")
  void readsRowsInOrder() throws IOException {
    Path csvFile = writeCsv("161,52,20,きのこ\n183,74,50,たけのこ\n");

    var people = KinokoTakenoko.loadPeople(csvFile);

    assertThat(people)
        .containsExactly(new Person(161, 52, 20, "きのこ"), new Person(183, 74, 50, "たけのこ"));
  }
}
