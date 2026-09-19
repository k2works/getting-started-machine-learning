package chapter08;

import chapter02.Row;
import chapter02.TrainTestSplit;
import java.util.List;
import java.util.stream.IntStream;

/** 訓練データとテストデータの正解率と、テストデータの生存者のうち生存と予測できた人数。 */
public record Evaluation(
    double trainAccuracy, double testAccuracy, int foundSurvivors, int survivors) {
  private static final int SURVIVED = 1;

  /** 学習済みのパイプラインを、訓練データとテストデータで評価する。 */
  public static Evaluation evaluate(FittedPipeline pipeline, TrainTestSplit<Row, Integer> split) {
    List<Integer> predictions = pipeline.predict(SurvivedData.features(split.xTest()));
    List<Integer> labels = split.tTest();
    return new Evaluation(
        accuracy(pipeline.predict(SurvivedData.features(split.xTrain())), split.tTrain()),
        accuracy(predictions, labels),
        (int)
            IntStream.range(0, labels.size())
                .filter(i -> predictions.get(i) == SURVIVED && labels.get(i) == SURVIVED)
                .count(),
        (int) labels.stream().filter(label -> label == SURVIVED).count());
  }

  private static double accuracy(List<Integer> predictions, List<Integer> labels) {
    long correct =
        IntStream.range(0, labels.size())
            .filter(i -> predictions.get(i).equals(labels.get(i)))
            .count();
    return (double) correct / labels.size();
  }
}
