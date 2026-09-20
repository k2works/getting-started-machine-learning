package machinelearning.chapter15

import java.nio.file.Paths
import machinelearning.chapter02.{Preprocessing, Table}
import machinelearning.chapter07.{Cinema, LinearRegression}
import machinelearning.chapter08.{ClassWeight, Pipeline, SurvivedData}

/** 第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。 */
object Training:
  private val TestSize = 0.2
  private val Seed = 0L
  private val MaxDepth = 5

  def trainAndSaveModels(dataDirectory: String, store: FileModelStore): Unit =
    val cinema = Cinema.prepare(Paths.get(dataDirectory, "cinema.csv"), TestSize, Seed)
    store.saveSalesModel(LinearRegression.fit(cinema.xTrain, cinema.tTrain))

    val rows = Table.load(Paths.get(dataDirectory, "Survived.csv")).rows
    val split = Preprocessing.splitTrainTest(rows, SurvivedData.target(rows), TestSize, Seed)
    val pipeline = Pipeline
      .build(Some(MaxDepth), ClassWeight.Balanced)
      .fit(SurvivedData.features(split.xTrain), split.tTrain)
    store.saveSurvivalModel(pipeline)
