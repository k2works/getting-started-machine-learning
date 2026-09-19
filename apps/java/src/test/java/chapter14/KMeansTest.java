package chapter14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import chapter07.Matrix;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class KMeansTest {
  @Nested
  @DisplayName("割り当て")
  class Assign {
    @Test
    @DisplayName("各点を最も近い中心のクラスタに割り当てる")
    void nearestCenter() {
      double[][] points = {{0}, {1}, {9}, {10}};
      double[][] centers = {{0}, {10}};

      assertThat(KMeans.assignClusters(points, centers)).containsExactly(0, 0, 1, 1);
    }

    @Test
    @DisplayName("2 次元の点をユークリッド距離で最も近い中心に割り当てる")
    void euclidean() {
      double[][] points = {{0, 0}, {5, 4}, {1, 0}};
      double[][] centers = {{5, 5}, {0, 0}};

      assertThat(KMeans.assignClusters(points, centers)).containsExactly(1, 0, 1);
    }
  }

  @Nested
  @DisplayName("中心の更新")
  class Update {
    @Test
    @DisplayName("クラスタごとに割り当てられた点の平均を新しい中心にする")
    void meanOfMembers() {
      double[][] points = {{0, 0}, {2, 0}, {10, 10}, {10, 12}};
      double[][] previous = {{0, 0}, {0, 0}};

      assertThat(KMeans.updateCenters(points, new int[] {0, 0, 1, 1}, previous))
          .isDeepEqualTo(new double[][] {{1, 0}, {10, 11}});
    }

    @Test
    @DisplayName("点が 1 つも割り当てられなかったクラスタは中心を変えない")
    void keepsEmptyCluster() {
      double[][] points = {{0, 0}, {2, 4}};
      double[][] previous = {{0, 0}, {99, 99}};

      assertThat(KMeans.updateCenters(points, new int[] {0, 0}, previous))
          .isDeepEqualTo(new double[][] {{1, 2}, {99, 99}});
    }
  }

  @Nested
  @DisplayName("SSE")
  class Sse {
    @Test
    @DisplayName("各点と所属するクラスタの中心との距離の 2 乗を合計する")
    void sumsSquaredDistances() {
      double[][] points = {{0, 0}, {2, 0}, {10, 10}, {10, 12}};
      double[][] centers = {{1, 0}, {10, 11}};

      assertThat(KMeans.sumOfSquaredErrors(points, new int[] {0, 0, 1, 1}, centers)).isEqualTo(4.0);
    }

    @Test
    @DisplayName("中心から離れた点ほど誤差が大きくなる")
    void fartherIsLarger() {
      assertThat(
              KMeans.sumOfSquaredErrors(
                  new double[][] {{0}, {4}}, new int[] {0, 0}, new double[][] {{1}}))
          .isEqualTo(10.0);
    }
  }

  private static double[][] twoGroups() {
    return new double[][] {{0, 0}, {0, 1}, {10, 10}, {10, 11}};
  }

  @Nested
  @DisplayName("繰り返し")
  class Fit {
    @Test
    @DisplayName("割り当てが変わらなくなるまで割り当てと中心の更新を繰り返す")
    void untilConverged() {
      KMeansResult result = KMeans.fit(twoGroups(), new double[][] {{0, 0}, {0, 1}});

      assertThat(result)
          .isEqualTo(
              new KMeansResult(
                  List.of(0, 0, 1, 1), Matrix.of(new double[][] {{0, 0.5}, {10, 10.5}}), 1.0));
    }

    @Test
    @DisplayName("最大反復回数に達したら収束していなくても打ち切る")
    void stopsAtMaxIterations() {
      KMeansResult result = KMeans.fit(twoGroups(), new double[][] {{0, 0}, {0, 1}}, 1);

      double[][] centers = result.centers().toArray();
      assertThat(centers[0]).containsExactly(0, 0);
      assertThat(centers[1]).containsExactly(new double[] {20.0 / 3, 22.0 / 3}, within(1e-9));
      assertThat(result.labels()).containsExactly(0, 0, 1, 1);
    }
  }

  private static double[][] numberedPoints(int size) {
    double[][] points = new double[size][];
    for (int i = 0; i < size; i++) {
      points[i] = new double[] {i, i * 2.0};
    }
    return points;
  }

  @Nested
  @DisplayName("初期中心の選択")
  class ChooseInitialCenters {
    @Test
    @DisplayName("データの中から重複なくクラスタ数だけ点を選ぶ")
    void distinctPointsFromData() {
      double[][] points = numberedPoints(10);

      double[][] centers = KMeans.chooseInitialCenters(points, 3, 0);

      assertThat(Arrays.stream(centers).map(Arrays::toString).distinct()).hasSize(3);
      for (double[] center : centers) {
        assertThat(Arrays.asList(points)).anySatisfy(point -> assertThat(point).isEqualTo(center));
      }
    }

    @Test
    @DisplayName("同じシードなら同じ点を選ぶ")
    void sameSeed() {
      double[][] points = numberedPoints(10);

      assertThat(KMeans.chooseInitialCenters(points, 3, 42))
          .isDeepEqualTo(KMeans.chooseInitialCenters(points, 3, 42));
    }

    @Test
    @DisplayName("シードが違えば違う点を選ぶ")
    void differentSeed() {
      double[][] points = numberedPoints(10);

      assertThat(
              Arrays.deepEquals(
                  KMeans.chooseInitialCenters(points, 3, 0),
                  KMeans.chooseInitialCenters(points, 3, 1)))
          .isFalse();
    }
  }

  @Nested
  @DisplayName("エルボー法")
  class Elbow {
    @Test
    @DisplayName("クラスタ数ごとにクラスタリングしたときの SSE を求める")
    void ssePerClusterCount() {
      assertThat(KMeans.sseByClusterCount(twoGroups(), List.of(1, 2), 0))
          .isEqualTo(Map.of(1, 201.0, 2, 1.0));
    }

    @Test
    @DisplayName("初期中心を変えて繰り返し最小の SSE を使う")
    void minimumOverRestarts() {
      assertThat(KMeans.sseByClusterCount(threePairs(), List.of(3), 0, 10))
          .isEqualTo(Map.of(3, 1.5));
    }
  }

  private static double[][] threePairs() {
    return new double[][] {{0}, {1}, {10}, {11}, {20}, {21}};
  }

  @Nested
  @DisplayName("局所解と複数回の試行")
  class Restarts {
    @Test
    @DisplayName("初期中心によっては局所解に陥る")
    void stuckInLocalOptimum() {
      KMeansResult stuck = KMeans.fit(threePairs(), new double[][] {{0}, {1}, {10}});

      assertThat(stuck.sse()).isEqualTo(101.0);
    }

    @Test
    @DisplayName("複数の初期中心の候補のうち SSE が最小の結果を返す")
    void bestOfCandidates() {
      List<double[][]> candidates =
          List.of(new double[][] {{0}, {1}, {10}}, new double[][] {{0}, {10}, {20}});

      KMeansResult result = KMeans.best(threePairs(), candidates);

      assertThat(result.sse()).isEqualTo(1.5);
      assertThat(result.centers()).isEqualTo(Matrix.of(new double[][] {{0.5}, {10.5}, {20.5}}));
    }
  }
}
