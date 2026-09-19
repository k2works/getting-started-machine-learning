package chapter08;

import chapter02.Table;
import java.io.Serializable;

/** fit で求めた値を使ってデータを変換する前処理。 */
@FunctionalInterface
public interface FittedTransformer extends Serializable {
  Table transform(Table x);

  /** この変換の後に next の変換を行う、合成した変換を返す。 */
  default FittedTransformer andThen(FittedTransformer next) {
    return x -> next.transform(transform(x));
  }

  /** 何も変えない変換。合成の初期値に使う。 */
  static FittedTransformer identity() {
    return x -> x;
  }
}
