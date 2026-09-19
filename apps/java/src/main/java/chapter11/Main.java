package chapter11;

import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Survived.csv と cinema.csv を K 分割交差検証で評価し、指標ごとの平均を表示する。 */
public final class Main {
  private Main() {}

  public static void main(String[] args) throws IOException {
    Path dataDir = DataDir.dataDir();
    System.out.println("Survived（決定木、" + Experiments.N_SPLITS + " 分割交差検証の平均）");
    print(Experiments.evaluateSurvived(dataDir.resolve("Survived.csv")), "%.4f");
    System.out.println("cinema（線形回帰、" + Experiments.N_SPLITS + " 分割交差検証の平均）");
    print(Experiments.evaluateCinema(dataDir.resolve("cinema.csv")), "%.2f");
  }

  private static void print(Map<String, Double> scores, String pattern) {
    scores.forEach(
        (name, score) ->
            System.out.println("  " + name + ": " + String.format(Locale.ROOT, pattern, score)));
  }
}
