package chapter03;

import chapter02.Features;
import java.util.Arrays;
import java.util.List;

/** テスト用の特徴量を作る。 */
final class Samples {
  private Samples() {}

  /** 1 列だけの特徴量を値の数だけ作る。 */
  static List<Features> column(String name, double... values) {
    return Arrays.stream(values)
        .mapToObj(v -> new Features(List.of(name), new double[] {v}))
        .toList();
  }

  /** 3 品種のうち、境界の右側に versicolor 2 件と virginica 1 件が残るデータ。 */
  static List<Features> threeSpeciesX() {
    return column("花弁幅", 0.1, 0.2, 0.3, 0.5, 0.6, 0.9);
  }

  static List<String> threeSpeciesT() {
    return List.of("setosa", "setosa", "setosa", "versicolor", "versicolor", "virginica");
  }
}
