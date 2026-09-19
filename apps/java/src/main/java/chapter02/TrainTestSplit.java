package chapter02;

import java.util.List;

/** 訓練データとテストデータ。X は特徴量の型、T は正解ラベルの型。 */
public record TrainTestSplit<X, T>(List<X> xTrain, List<X> xTest, List<T> tTrain, List<T> tTest) {
  public TrainTestSplit {
    xTrain = List.copyOf(xTrain);
    xTest = List.copyOf(xTest);
    tTrain = List.copyOf(tTrain);
    tTest = List.copyOf(tTest);
  }
}
