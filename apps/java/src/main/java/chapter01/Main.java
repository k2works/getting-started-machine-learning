package chapter01;

import dataset.DataDir;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

/** 実データでルールによる判定の正解率を表示する。 */
public final class Main {
  private Main() {}

  public static void main(String[] args) throws IOException {
    List<Person> people = KinokoTakenoko.loadPeople(DataDir.dataDir().resolve("KvsT.csv"));
    FeaturesAndLabels split = KinokoTakenoko.splitFeaturesAndLabels(people);
    List<String> predictions =
        split.features().stream().map(KinokoTakenoko::predictByRule).toList();
    System.out.println("データ件数: " + people.size());
    System.out.println(
        "ルールによる判定の正解率: "
            + String.format(
                Locale.ROOT, "%.4f", KinokoTakenoko.accuracy(predictions, split.labels())));
  }
}
