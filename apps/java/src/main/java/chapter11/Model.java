package chapter11;

import chapter02.Features;
import java.util.List;

/** 交差検証で学習と予測を繰り返すモデル。T は正解ラベルの型。 */
public interface Model<T> {
  /** 訓練データで学習する。 */
  void fit(List<Features> x, List<T> t);

  /** 学習した結果で予測する。 */
  List<T> predict(List<Features> x);
}
