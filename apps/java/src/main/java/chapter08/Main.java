package chapter08;

import chapter02.Preprocessing;
import chapter02.Row;
import chapter02.Table;
import chapter02.TrainTestSplit;
import dataset.DataDir;
import java.io.IOException;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** クラスの重みごとの評価結果を表示し、学習済みのパイプラインを保存して読み込む。 */
public final class Main {
  /** 学習済みのパイプラインの保存先（apps/java/model/ は .gitignore の対象） */
  public static final Path MODEL_FILE = Path.of("model/survived.ser");

  private static final double TEST_SIZE = 0.2;
  private static final long SEED = 0;
  private static final int MAX_DEPTH = 5;

  /** 年齢が分からない架空の乗客 2 人（1 等客室の女性と、3 等客室の男性） */
  private static final Table NEW_PASSENGERS =
      SurvivedData.features(
          List.of(
              passenger("1", "female", "", "0", "0", "50", "C"),
              passenger("3", "male", "", "0", "0", "8", "S")));

  private Main() {}

  /** 特徴量の列の順に並べた値から、乗客 1 人分の行を作る。空文字列は欠損値。 */
  private static Row passenger(String... values) {
    Map<String, String> cells = new HashMap<>();
    for (int i = 0; i < values.length; i++) {
      cells.put(SurvivedData.FEATURES.get(i), values[i]);
    }
    return new Row(cells);
  }

  public static void main(String[] args) throws IOException, ClassNotFoundException {
    main(MODEL_FILE);
  }

  /** 保存先を指定して実行する。テストから一時ディレクトリを渡すために使う。 */
  public static void main(Path modelFile) throws IOException, ClassNotFoundException {
    List<Row> rows = Table.load(DataDir.dataDir().resolve("Survived.csv")).rows();
    List<Integer> t = SurvivedData.target(rows);
    TrainTestSplit<Row, Integer> split = Preprocessing.splitTrainTest(rows, t, TEST_SIZE, SEED);
    long survived = t.stream().filter(label -> label == 1).count();
    System.out.println(
        "データ件数: " + rows.size() + "（生存 " + survived + ", 死亡 " + (t.size() - survived) + "）");
    System.out.println(
        "訓練データ: " + split.xTrain().size() + " 件, テストデータ: " + split.xTest().size() + " 件");

    Map<ClassWeight, FittedPipeline> pipelines = new EnumMap<>(ClassWeight.class);
    for (ClassWeight classWeight : ClassWeight.values()) {
      FittedPipeline pipeline =
          Pipeline.build(MAX_DEPTH, classWeight)
              .fit(SurvivedData.features(split.xTrain()), split.tTrain());
      pipelines.put(classWeight, pipeline);
      Evaluation result = Evaluation.evaluate(pipeline, split);
      System.out.printf(
          Locale.ROOT,
          "classWeight=%s: 訓練 %.3f, テスト %.3f, 生存者 %d 人中 %d 人を発見%n",
          classWeight,
          result.trainAccuracy(),
          result.testAccuracy(),
          result.survivors(),
          result.foundSurvivors());
    }

    ModelFiles.save(pipelines.get(ClassWeight.BALANCED), modelFile);
    List<Integer> predictions = ModelFiles.load(modelFile).predict(NEW_PASSENGERS);
    System.out.println("保存したモデル: " + modelFile.getFileName());
    System.out.println("架空の乗客の予測: " + predictions);
  }
}
