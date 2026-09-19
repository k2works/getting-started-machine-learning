package chapter09;

import chapter02.Features;
import java.util.List;

/** テストで使う架空の特徴量。 */
final class Samples {
  private Samples() {}

  /** RM の 1 列だけの特徴量。 */
  static Features rm(double value) {
    return new Features(List.of("RM"), new double[] {value});
  }
}
