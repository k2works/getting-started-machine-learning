import { join } from "node:path";
import { dataDir } from "../dataset.ts";
import {
  type BostonDataset,
  FEATURES,
  OUTLIER_THRESHOLD,
  TARGET,
  loadBoston,
  prepareBoston,
  removeOutliers,
} from "./boston-features.ts";
import { fitLasso } from "./lasso.ts";
import {
  type Experiment,
  bestExperiment,
  fitRidge,
  predict,
  r2Score,
  runRidgeExperiments,
  zeroCoefficientNames,
} from "./regularization.ts";

const TEST_SIZE = 0.3;
const VALIDATION_SIZE = 0.3;
const SEED = 0;
const ALPHAS = [0, 0.1, 1, 10, 100];
const LASSO_LAMBDA = 0.05;
const SEEDS_TO_COMPARE = [0, 1, 2, 3, 4];

interface Comparison {
  experiments: readonly Experiment[];
  best: Experiment;
  linearScore: number;
  ridgeScore: number;
}

/** 検証データで alpha を選び、線形回帰と選んだリッジ回帰をテストデータで 1 回だけ評価する */
function compareOnTestData(dataset: BostonDataset): Comparison {
  const experiments = runRidgeExperiments(dataset, ALPHAS);
  const best = bestExperiment(experiments);
  const score = (alpha: number) =>
    r2Score(
      dataset.tTest,
      predict(fitRidge(dataset.xTrain, dataset.tTrain, alpha), dataset.xTest),
    );
  return {
    experiments,
    best,
    linearScore: score(0),
    ridgeScore: score(best.alpha),
  };
}

export function main(print: (line: string) => void = console.log): void {
  const csvFile = join(dataDir(), "Boston.csv");
  const rows = loadBoston(csvFile);
  const kept = removeOutliers(rows, [...FEATURES, TARGET], OUTLIER_THRESHOLD);
  const dataset = prepareBoston(csvFile, TEST_SIZE, VALIDATION_SIZE, SEED);
  print(
    `データ件数: ${kept.length}（外れ値 ${rows.length - kept.length} 件を除外）`,
  );
  print(
    `訓練データ: ${dataset.tTrain.length} 件, 検証データ: ${dataset.tValid.length} 件, テストデータ: ${dataset.tTest.length} 件`,
  );
  print(`特徴量: ${dataset.featureNames.join(", ")}`);

  const { experiments, best, linearScore, ridgeScore } =
    compareOnTestData(dataset);
  print("alpha\t訓練 R²\t検証 R²\t係数の絶対値の合計");
  for (const e of experiments) {
    print(
      `${e.alpha}\t${e.trainScore.toFixed(4)}\t${e.validationScore.toFixed(4)}\t${e.coefficientAbsSum.toFixed(3)}`,
    );
  }
  print(`検証データで選んだ alpha: ${best.alpha}`);
  print(
    `テストデータの決定係数: 線形回帰 ${linearScore.toFixed(4)}, リッジ回帰 ${ridgeScore.toFixed(4)}`,
  );

  const lasso = fitLasso(dataset.xTrain, dataset.tTrain, LASSO_LAMBDA);
  const zeros = zeroCoefficientNames(lasso.coefficients, dataset.featureNames);
  print(
    `ラッソ回帰（lambda=${LASSO_LAMBDA}）で係数が 0 になった特徴量: ${zeros.join(", ")}`,
  );

  print("");
  print("シード\t選んだ alpha\t線形回帰\tリッジ回帰");
  for (const seed of SEEDS_TO_COMPARE) {
    const c = compareOnTestData(
      prepareBoston(csvFile, TEST_SIZE, VALIDATION_SIZE, seed),
    );
    print(
      `${seed}\t${c.best.alpha}\t${c.linearScore.toFixed(4)}\t${c.ridgeScore.toFixed(4)}`,
    );
  }
}

// node src/chapter12/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
