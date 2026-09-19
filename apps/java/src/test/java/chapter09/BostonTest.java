package chapter09;

import static org.assertj.core.api.Assertions.assertThat;

import chapter02.Features;
import chapter02.TrainTestSplit;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BostonTest {
  @TempDir Path directory;

  @Test
  @DisplayName("ダミー変数化と欠損値の補完をして特徴量と価格に分ける")
  void preparesBoston() throws IOException {
    Path csvFile = directory.resolve("boston.csv");
    Files.writeString(
        csvFile,
        """
        CRIME,RM,NOX,PRICE
        low,6.0,,20.0
        high,5.0,0.5,15.0
        very_low,7.0,0.4,30.0
        low,6.5,0.6,25.0
        """);

    TrainTestSplit<Features, Double> split = Boston.prepare(csvFile, 0.5, 0);

    assertThat(split.xTrain().getFirst().columns())
        .containsExactly("RM", "NOX", "CRIME_low", "CRIME_very_low");
    assertThat(split.xTrain()).hasSize(2);
    assertThat(split.xTest()).hasSize(2);
    assertThat(split.tTrain()).hasSize(2);
    assertThat(split.tTest()).hasSize(2);
  }
}
