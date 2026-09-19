package chapter15;

import chapter02.Preprocessing;
import chapter02.Row;
import chapter02.Table;
import chapter02.TrainTestSplit;
import chapter07.Cinema;
import chapter07.LinearRegression;
import chapter08.ClassWeight;
import chapter08.Pipeline;
import chapter08.SurvivedData;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** 第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。 */
public final class Training {
  private static final double TEST_SIZE = 0.2;
  private static final long SEED = 0;
  private static final int MAX_DEPTH = 5;

  private Training() {}

  public static void trainAndSaveModels(Path dataDir, FileModelStore store) throws IOException {
    var cinema = Cinema.prepare(dataDir.resolve("cinema.csv"), TEST_SIZE, SEED);
    store.saveSalesModel(LinearRegression.fit(cinema.xTrain(), cinema.tTrain()));

    List<Row> rows = Table.load(dataDir.resolve("Survived.csv")).rows();
    TrainTestSplit<Row, Integer> survived =
        Preprocessing.splitTrainTest(rows, SurvivedData.target(rows), TEST_SIZE, SEED);
    store.saveSurvivalModel(
        Pipeline.build(MAX_DEPTH, ClassWeight.BALANCED)
            .fit(SurvivedData.features(survived.xTrain()), survived.tTrain()));
  }
}
