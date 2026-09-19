package chapter12;

import static org.assertj.core.api.Assertions.assertThat;

import chapter07.Matrix;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.assertj.core.util.DoubleComparator;

/** 第 12 章のテストで使う人工データとアサーション。 */
final class Samples {
  private Samples() {}

  /**
   * 特徴量と正解の組。
   *
   * @param x 特徴量の行列
   * @param t 正解
   */
  record Data(Matrix x, List<Double> t) {}

  /** 正規分布の 4 列の特徴量 30 件と、その重み付きの和に雑音を足した正解。 */
  static Data randomDataset() {
    var random = new Random(0);
    double[] weights = {1.5, -2.0, 0.5, 3.0};
    double[][] rows = new double[30][weights.length];
    List<Double> t = new ArrayList<>();
    for (double[] row : rows) {
      double sum = 0;
      for (int j = 0; j < weights.length; j++) {
        row[j] = random.nextGaussian();
        sum += row[j] * weights[j];
      }
      t.add(sum + 0.5 * random.nextGaussian());
    }
    return new Data(Matrix.of(rows), t);
  }

  /** 要素ごとに、差が tolerance 以内であることを確かめる。 */
  static void assertDoubles(List<Double> expected, List<Double> actual, double tolerance) {
    assertThat(actual)
        .usingElementComparator(new DoubleComparator(tolerance))
        .containsExactlyElementsOf(expected);
  }
}
