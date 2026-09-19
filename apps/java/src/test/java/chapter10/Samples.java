package chapter10;

import chapter02.Features;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

/** テスト用の特徴量を作る。値は架空のもの。 */
final class Samples {
  private Samples() {}

  /** 1 列だけの特徴量を値の数だけ作る。 */
  static List<Features> column(String name, double... values) {
    return Arrays.stream(values)
        .mapToObj(v -> new Features(List.of(name), new double[] {v}))
        .toList();
  }

  /** 2 列の特徴量を、列ごとの値から作る。 */
  static List<Features> columns(String first, double[] firsts, String second, double[] seconds) {
    return IntStream.range(0, firsts.length)
        .mapToObj(i -> new Features(List.of(first, second), new double[] {firsts[i], seconds[i]}))
        .toList();
  }

  /** がく片幅では分けられず、花弁幅で分けられる 2 品種 10 件のデータ。 */
  static List<Features> twoSpeciesX() {
    return columns(
        "がく片幅",
        new double[] {0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4, 0.5, 0.3},
        "花弁幅",
        new double[] {0.1, 0.12, 0.14, 0.16, 0.18, 0.8, 0.82, 0.84, 0.86, 0.88});
  }

  static List<String> twoSpeciesT() {
    return List.of(
        "setosa",
        "setosa",
        "setosa",
        "setosa",
        "setosa",
        "virginica",
        "virginica",
        "virginica",
        "virginica",
        "virginica");
  }

  /** twoSpeciesX と同じ列の、品種が 1 件ずつの新しいデータ。 */
  static List<Features> twoSpeciesNewX() {
    return columns("がく片幅", new double[] {0.4, 0.4}, "花弁幅", new double[] {0.13, 0.83});
  }
}
