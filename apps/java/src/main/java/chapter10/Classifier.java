package chapter10;

import chapter02.Features;
import java.util.List;

/** fit で学習し、predict でラベルを予測する分類器。 */
public interface Classifier {
  /** 訓練データで学習し、自分自身を返す。 */
  Classifier fit(List<Features> x, List<String> t);

  /** 特徴量ごとのラベルを予測する。 */
  List<String> predict(List<Features> x);
}
