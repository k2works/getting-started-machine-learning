package chapter10;

import chapter01.KinokoTakenoko;
import chapter02.Features;
import chapter02.TrainTestSplit;

/** 訓練データとテストデータの正解率。 */
public record Score(double train, double test) {
  /** モデルを訓練データで学習させてから、訓練データとテストデータの正解率を求める。 */
  public static Score evaluate(Classifier model, TrainTestSplit<Features, String> split) {
    model.fit(split.xTrain(), split.tTrain());
    return new Score(
        KinokoTakenoko.accuracy(model.predict(split.xTrain()), split.tTrain()),
        KinokoTakenoko.accuracy(model.predict(split.xTest()), split.tTest()));
  }
}
