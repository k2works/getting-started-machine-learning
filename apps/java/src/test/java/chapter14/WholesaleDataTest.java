package chapter14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import chapter02.Features;
import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import support.StdoutCapture;

class WholesaleDataTest {
  private final Path csvFile = DataDir.dataDir().resolve("Wholesale.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(Files.exists(csvFile), "学習データ Wholesale.csv が配置されていない（gulp data:setup）");
  }

  @Test
  @DisplayName("実データから 440 件の支出額 6 列を読み込む")
  void loads440Rows() throws IOException {
    List<Features> x = Spending.load(csvFile);

    assertThat(x).hasSize(440);
    assertThat(x.getFirst().columns()).hasSize(6);
  }

  @Test
  @DisplayName("標準化したデータのクラスタ数 1 の SSE は件数と列数の積になる")
  void sseOfOneClusterIsCountTimesColumns() throws IOException {
    double[][] points = Spending.standardize(Spending.load(csvFile));

    double sse = KMeans.sseByClusterCount(points, List.of(1), 0).get(1);

    assertThat(sse).isCloseTo(440.0 * 6, within(1e-6));
  }

  @Test
  @DisplayName("クラスタ数を増やすほど SSE が小さくなる")
  void sseDecreases() throws IOException {
    double[][] points = Spending.standardize(Spending.load(csvFile));

    List<Double> sse =
        new ArrayList<>(
            KMeans.sseByClusterCount(points, IntStream.rangeClosed(1, 10).boxed().toList(), 0)
                .values());

    for (int i = 1; i < sse.size(); i++) {
      assertThat(sse.get(i)).isLessThan(sse.get(i - 1));
    }
  }

  @Test
  @DisplayName("実行すると SSE とクラスタごとの件数と平均支出額を表示する")
  void printsSummary() throws Exception {
    String output = StdoutCapture.capture(() -> Main.main(new String[0]));

    assertThat(output)
        .isEqualTo(
            """
            データ件数: 440（支出額 6 列）
            クラスタ数ごとの SSE（初期中心 10 通りの最小値）:
            クラスタ数\t自作\tTribuo（k-means++）
            1\t2640.00\t2640.00
            2\t1954.18\t1954.78
            3\t1614.52\t1607.67
            4\t1334.36\t1317.90
            5\t1085.27\t1058.77
            6\t947.20\t917.67
            7\t888.22\t839.38
            8\t775.24\t742.02
            9\t690.81\t655.14
            10\t618.17\t606.81

            クラスタ数 5 のクラスタごとの件数と平均支出額:
            クラスタ\t件数\tFresh\tMilk\tGrocery\tFrozen\tDetergents_Paper\tDelicassen
            2\t265\t8909\t2967\t3804\t2248\t989\t962
            1\t96\t5509\t10556\t16478\t1420\t7199\t1659
            0\t65\t31117\t4260\t5374\t7225\t849\t2286
            3\t10\t15965\t34709\t48537\t3055\t24875\t2943
            4\t4\t52022\t31696\t18491\t29826\t2699\t19656
            """);
  }
}
