package chapter13;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import chapter02.Features;
import chapter07.Matrix;
import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import support.StdoutCapture;

class BostonPcaDataTest {
  private final Path csvFile = DataDir.dataDir().resolve("Boston.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(Files.exists(csvFile), "学習データ Boston.csv が配置されていない（gulp data:setup）");
  }

  @Test
  @DisplayName("CRIME をダミー変数にして 15 列の標準化済みデータにする")
  void loads100RowsAnd15Columns() throws IOException {
    List<Features> x = BostonPca.load(csvFile);

    assertThat(x).hasSize(100);
    assertThat(x.getFirst().columns()).hasSize(15);
  }

  @Test
  @DisplayName("実データの主成分も分散共分散行列の固有ベクトルになる")
  void eigenvectorsOfRealData() throws IOException {
    Matrix x = BostonPca.toMatrix(BostonPca.load(csvFile));

    PcaModel model = Pca.fit(x, 15);

    Matrix covariance = Pca.covarianceMatrix(x);
    double[][] components = model.components().toArray();
    for (int i = 0; i < components.length; i++) {
      double variance = model.explainedVariance().get(i);
      Matrix projected = covariance.times(Matrix.columnVector(components[i]));
      double[] expected = Arrays.stream(components[i]).map(v -> v * variance).toArray();
      assertThat(projected.column(0)).containsExactly(expected, within(1e-9));
    }
    double total = model.explainedVarianceRatio().stream().mapToDouble(Double::doubleValue).sum();
    assertThat(total).isCloseTo(1.0, within(1e-9));
  }

  @Test
  @DisplayName("実行すると寄与率と主成分の解釈を表示する")
  void printsSummary() throws Exception {
    String output = StdoutCapture.capture(() -> Main.main(new String[0]));

    assertThat(output)
        .isEqualTo(
            """
            データ件数: 100, 列数: 15
            寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581
            累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）
            第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328
            第 2 主成分で影響の大きい列: PRICE 0.444, CRIME_low 0.423, RM 0.405
            """);
  }
}
