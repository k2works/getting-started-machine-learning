import { join } from "node:path";
import { splitTrainTest } from "../chapter02/iris-preprocessing.ts";
import {
  fitLinearRegression,
  prepareCinema,
} from "../chapter07/cinema-regression.ts";
import { fitPipeline } from "../chapter08/pipeline.ts";
import {
  loadSurvived,
  splitFeaturesAndTarget,
} from "../chapter08/survived-data.ts";
import type { FileModelStore } from "./file-model-store.ts";

const TEST_SIZE = 0.2;
const SEED = 0;
const MAX_DEPTH = 5;

/** 第 7・8 章と同じ条件でモデルを学習し、置き場に保存する */
export function trainAndSaveModels(
  dataDirectory: string,
  store: FileModelStore,
): void {
  const cinema = prepareCinema(
    join(dataDirectory, "cinema.csv"),
    TEST_SIZE,
    SEED,
  );
  store.saveSalesModel(fitLinearRegression(cinema.xTrain, cinema.tTrain));

  const { x, t } = splitFeaturesAndTarget(
    loadSurvived(join(dataDirectory, "Survived.csv")),
  );
  const survived = splitTrainTest(x, t, TEST_SIZE, SEED);
  store.saveSurvivalModel(
    fitPipeline(survived.xTrain, survived.tTrain, {
      maxDepth: MAX_DEPTH,
      classWeight: "balanced",
    }),
  );
}
