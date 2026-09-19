package chapter14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import java.util.Collection;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tribuo.clustering.kmeans.KMeansTrainer;

class TribuoKMeansTest {
  @Test
  @DisplayName("KMeansTrainer の初期化方法は RANDOM と PLUSPLUS だけで初期中心を渡すコンストラクターは無い")
  void cannotPassInitialCenters() {
    assertThat(KMeansTrainer.Initialisation.values())
        .extracting(Enum::name)
        .containsExactly("RANDOM", "PLUSPLUS");
    assertThat(KMeansTrainer.class.getConstructors())
        .noneMatch(
            constructor ->
                Arrays.stream(constructor.getParameterTypes())
                    .anyMatch(t -> t.isArray() || Collection.class.isAssignableFrom(t)));
  }

  @Test
  @DisplayName("はっきり分かれた 2 グループなら Tribuo の SSE も自作と同じになる")
  void sameSseForSeparatedGroups() {
    double[][] points = {{0, 0}, {0, 1}, {10, 10}, {10, 11}};

    assertThat(TribuoKMeans.sse(points, 2, 0L)).isCloseTo(1.0, within(1e-9));
  }

  @Test
  @DisplayName("Tribuo でもシードを変えて繰り返し最小の SSE を使える")
  void bestSseOverSeeds() {
    double[][] points = {{0}, {1}, {10}, {11}, {20}, {21}};

    assertThat(TribuoKMeans.bestSse(points, 3, 0L, 10)).isCloseTo(1.5, within(1e-9));
  }
}
