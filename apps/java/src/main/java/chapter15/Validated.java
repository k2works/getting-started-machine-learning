package chapter15;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** 入力の検証結果。正しければ値を、不正なら理由の一覧を持つ。 */
public sealed interface Validated<T> {
  /** 正しい入力から作った値。 */
  record Valid<T>(T value) implements Validated<T> {}

  /** 不正な入力の理由の一覧。 */
  record Invalid<T>(List<String> errors) implements Validated<T> {
    public Invalid {
      errors = List.copyOf(errors);
    }
  }

  /** 理由（null は問題なし）が 1 つも無ければ値を作り、あれば理由の一覧を返す。 */
  static <T> Validated<T> of(List<String> reasons, Supplier<T> value) {
    List<String> errors = reasons.stream().filter(Objects::nonNull).toList();
    return errors.isEmpty() ? new Valid<>(value.get()) : new Invalid<>(errors);
  }
}
