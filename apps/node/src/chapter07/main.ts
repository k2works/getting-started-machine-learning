import { join } from "node:path";
import { dataDir } from "../dataset.ts";
import {
  FEATURES,
  fitLinearRegression,
  loadCinema,
  predict,
  prepareCinema,
  removeOutliers,
} from "./cinema-regression.ts";
import {
  meanAbsoluteError,
  r2Score,
  rootMeanSquaredError,
} from "./regression-metrics.ts";

const TEST_SIZE = 0.2;
const SEED = 0;

export function main(print: (line: string) => void = console.log): void {
  const csvFile = join(dataDir(), "cinema.csv");
  const rows = loadCinema(csvFile);
  const split = prepareCinema(csvFile, TEST_SIZE, SEED);
  const model = fitLinearRegression(split.xTrain, split.tTrain);
  const y = predict(model, split.xTest);
  const coefficients = FEATURES.map(
    (name) => `${name}=${model.coefficients[name].toFixed(4)}`,
  ).join(", ");
  print(`データ件数: ${rows.length}`);
  print(`外れ値を除いた件数: ${removeOutliers(rows).length}`);
  print(
    `訓練データ: ${split.xTrain.length} 件, テストデータ: ${split.xTest.length} 件`,
  );
  print(`切片: ${model.intercept.toFixed(2)}`);
  print(`係数: ${coefficients}`);
  print(
    `テストデータの評価: R2=${r2Score(split.tTest, y).toFixed(4)}, ` +
      `MAE=${meanAbsoluteError(split.tTest, y).toFixed(2)}, ` +
      `RMSE=${rootMeanSquaredError(split.tTest, y).toFixed(2)}`,
  );
}

// node src/chapter07/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
