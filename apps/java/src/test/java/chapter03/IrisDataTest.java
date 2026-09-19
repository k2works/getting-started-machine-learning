package chapter03;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import chapter01.KinokoTakenoko;
import chapter02.Features;
import chapter02.Preprocessing;
import chapter02.TrainTestSplit;
import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import support.StdoutCapture;

class IrisDataTest {
  private final Path csvFile = DataDir.dataDir().resolve("iris.csv");

  @BeforeEach
  void requireData() {
    assumeTrue(Files.exists(csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");
  }

  private TrainTestSplit<Features, String> irisSplit() throws IOException {
    return Preprocessing.prepareIris(csvFile, 0.3, 0);
  }

  @Test
  @DisplayName("深さ 2 の決定木はテストデータの 45 件中 43 件を正しく分類する")
  void depthTwoAccuracy() throws IOException {
    var split = irisSplit();

    var predictions =
        DecisionTree.withMaxDepth(2).fit(split.xTrain(), split.tTrain()).predict(split.xTest());

    assertThat(KinokoTakenoko.accuracy(predictions, split.tTest()))
        .isCloseTo(43.0 / 45, within(1e-12));
  }

  @ParameterizedTest(name = "深さ {0}")
  @ValueSource(ints = {1, 2, 3, 4, 5, TribuoTrees.UNLIMITED})
  @DisplayName("この分割ではどの深さでも Tribuo の CART とテストデータの予測が一致する")
  void sameAsTribuo(int maxDepth) throws IOException {
    var split = irisSplit();
    DecisionTree mine =
        maxDepth == TribuoTrees.UNLIMITED
            ? DecisionTree.unlimited()
            : DecisionTree.withMaxDepth(maxDepth);

    List<String> ours = mine.fit(split.xTrain(), split.tTrain()).predict(split.xTest());
    List<String> tribuo =
        TribuoTrees.predict(
            TribuoTrees.train(split.xTrain(), split.tTrain(), maxDepth), split.xTest());

    assertThat(IntStream.range(0, ours.size()).filter(i -> !ours.get(i).equals(tribuo.get(i))))
        .isEmpty();
  }

  @Test
  @DisplayName("実行すると深さごとの正解率と深さ 2 の決定木を表示する")
  void mainPrintsSummary() throws Exception {
    String output = StdoutCapture.capture(() -> Main.main(new String[0]));

    assertThat(output)
        .isEqualTo(
            """
            深さ\t訓練データ\tテストデータ
            1\t0.6762\t0.6444
            2\t0.9333\t0.9556
            3\t0.9524\t0.9556
            4\t0.9619\t0.9556
            5\t0.9810\t0.9333
            制限なし\t1.0000\t0.9333

            深さ 2 の決定木:
            花弁幅 <= 0.2950
              Iris-setosa
            花弁幅 > 0.2950
              花弁幅 <= 0.6500
                Iris-versicolor
              花弁幅 > 0.6500
                Iris-virginica
            """);
  }
}
