package chapter02;

import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;

/** アヤメのデータの前処理の結果を表示する。 */
public final class Main {
  private static final double TEST_SIZE = 0.3;
  private static final long SEED = 0;

  private Main() {}

  public static void main(String[] args) throws IOException {
    Path csvFile = DataDir.dataDir().resolve("iris.csv");
    Table table = Table.load(csvFile);
    TrainTestSplit<Features, String> split = Preprocessing.prepareIris(csvFile, TEST_SIZE, SEED);
    System.out.println("データ件数: " + table.rows().size());
    System.out.println("欠損値の数: " + formatCounts(table.countMissing()));
    System.out.println(
        "訓練データ: " + split.xTrain().size() + " 件, テストデータ: " + split.xTest().size() + " 件");
    System.out.println("特徴量: " + String.join(", ", split.xTrain().getFirst().columns()));
  }

  private static String formatCounts(Map<String, Integer> counts) {
    return counts.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(Collectors.joining(", "));
  }
}
