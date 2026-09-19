package chapter08;

import chapter02.Table;

/** 訓練データから変換に必要な値を求める前処理。 */
public interface Transformer {
  FittedTransformer fit(Table x);
}
