import { join } from "node:path";
import { type Feature, prepareIris } from "../chapter02/iris-preprocessing.ts";
import { DecisionTree } from "../chapter03/decision-tree.ts";
import { dataDir } from "../dataset.ts";
import { type Classifier, evaluate } from "./classifier.ts";
import { forestImportances } from "./feature-importance.ts";
import { LogisticRegression } from "./logistic-regression.ts";
import { mlLogisticRegression, mlRandomForest } from "./ml-classifier.ts";
import { RandomForest } from "./random-forest.ts";

const TEST_SIZE = 0.3;
const SEED = 0;
const N_ESTIMATORS = 100;
const MAX_FEATURES = 2;
const SHALLOW_DEPTH = 2;

export function models(): [string, Classifier<Feature>][] {
  const forest = { nEstimators: N_ESTIMATORS, maxFeatures: MAX_FEATURES };
  return [
    [
      `決定木（深さ ${SHALLOW_DEPTH}）`,
      new DecisionTree({ maxDepth: SHALLOW_DEPTH }),
    ],
    ["ロジスティック回帰", new LogisticRegression()],
    [
      `ランダムフォレスト（${N_ESTIMATORS} 本）`,
      new RandomForest({ ...forest, seed: SEED }),
    ],
    [
      `ランダムフォレスト（${N_ESTIMATORS} 本・深さ ${SHALLOW_DEPTH}）`,
      new RandomForest({ ...forest, maxDepth: SHALLOW_DEPTH, seed: SEED }),
    ],
    ["ml-logistic-regression（切片なし）", mlLogisticRegression()],
    [
      "ml-logistic-regression（値が 1 の列を追加）",
      mlLogisticRegression({ intercept: true }),
    ],
    [
      `ml-random-forest（${N_ESTIMATORS} 本）`,
      mlRandomForest({ ...forest, seed: SEED }),
    ],
  ];
}

export function main(print: (line: string) => void = console.log): void {
  const split = prepareIris(join(dataDir(), "iris.csv"), TEST_SIZE, SEED);
  print("モデル\t訓練データ\tテストデータ");
  for (const [name, model] of models()) {
    const score = evaluate(model, split);
    print(`${name}\t${score.train.toFixed(4)}\t${score.test.toFixed(4)}`);
  }

  const forest = new RandomForest<Feature>({
    nEstimators: N_ESTIMATORS,
    maxFeatures: MAX_FEATURES,
    seed: SEED,
  }).fit(split.xTrain, split.tTrain);
  print("");
  print(`ランダムフォレスト（${N_ESTIMATORS} 本）の特徴量の重要度:`);
  const importances = forestImportances(forest, split.xTrain, split.tTrain);
  for (const [feature, value] of Object.entries(importances)) {
    print(`${feature}\t${value.toFixed(4)}`);
  }
}

// node src/chapter10/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
