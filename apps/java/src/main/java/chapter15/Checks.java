package chapter15;

import java.util.Set;
import java.util.stream.Collectors;

/** 入力の検証に使う規則。問題が無ければ null を返し、あれば理由を返す。 */
final class Checks {
  private Checks() {}

  static String required(String field, Object value) {
    return value == null ? field + " は必須です" : null;
  }

  static String notNegative(String field, Number value) {
    return value != null && value.doubleValue() < 0 ? field + " は 0 以上にしてください" : null;
  }

  static <T> String oneOf(String field, T value, Set<T> allowed) {
    if (value == null || allowed.contains(value)) {
      return null;
    }
    String choices =
        allowed.stream().map(String::valueOf).sorted().collect(Collectors.joining("、"));
    return field + " は " + choices + " のどれかにしてください";
  }
}
