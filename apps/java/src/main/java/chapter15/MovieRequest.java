package chapter15;

import java.util.Arrays;
import java.util.Set;

/** 興行収入の予測の要求。JSON に無い値は null になるので、検証で必須かどうかを確かめる。 */
public record MovieRequest(Double sns1, Double sns2, Double actor, Integer original) {
  private static final Set<Integer> ORIGINAL_VALUES = Set.of(0, 1);

  /** 検証して、正しければ映画の特徴量にする。 */
  public Validated<Movie> validate() {
    return Validated.of(
        Arrays.asList(
            Checks.required("sns1", sns1),
            Checks.required("sns2", sns2),
            Checks.required("actor", actor),
            Checks.required("original", original),
            Checks.notNegative("sns1", sns1),
            Checks.notNegative("sns2", sns2),
            Checks.notNegative("actor", actor),
            Checks.oneOf("original", original, ORIGINAL_VALUES)),
        () -> new Movie(sns1, sns2, actor, original));
  }
}
