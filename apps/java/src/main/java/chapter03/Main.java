package chapter03;

import chapter01.KinokoTakenoko;
import chapter02.Features;
import chapter02.Preprocessing;
import chapter02.TrainTestSplit;
import dataset.DataDir;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** 深さごとの正解率と、深さ 2 の決定木を表示する。 */
public final class Main {
  private static final double TEST_SIZE = 0.3;
  private static final long SEED = 0;
  private static final List<Integer> MAX_DEPTHS = List.of(1, 2, 3, 4, 5);
  private static final int TREE_DEPTH_TO_SHOW = 2;

  private Main() {}

  public static void main(String[] args) throws IOException {
    TrainTestSplit<Features, String> split =
        Preprocessing.prepareIris(DataDir.dataDir().resolve("iris.csv"), TEST_SIZE, SEED);
    System.out.println("深さ\t訓練データ\tテストデータ");
    for (int maxDepth : MAX_DEPTHS) {
      printAccuracy(String.valueOf(maxDepth), DecisionTree.withMaxDepth(maxDepth), split);
    }
    printAccuracy("制限なし", DecisionTree.unlimited(), split);

    DecisionTree shallow =
        DecisionTree.withMaxDepth(TREE_DEPTH_TO_SHOW).fit(split.xTrain(), split.tTrain());
    System.out.println();
    System.out.println("深さ " + TREE_DEPTH_TO_SHOW + " の決定木:");
    System.out.println(DecisionTrees.format(shallow.tree().orElseThrow()));
  }

  private static void printAccuracy(
      String label, DecisionTree model, TrainTestSplit<Features, String> split) {
    model.fit(split.xTrain(), split.tTrain());
    double train = KinokoTakenoko.accuracy(model.predict(split.xTrain()), split.tTrain());
    double test = KinokoTakenoko.accuracy(model.predict(split.xTest()), split.tTest());
    System.out.println(label + "\t" + format(train) + "\t" + format(test));
  }

  private static String format(double value) {
    return String.format(Locale.ROOT, "%.4f", value);
  }
}
