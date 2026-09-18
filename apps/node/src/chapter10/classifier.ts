import { accuracy } from "../chapter01/kinoko-takenoko.ts";
import type { TrainTestSplit } from "../chapter02/iris-preprocessing.ts";

/** fit で学習し、predict でラベルを予測する分類モデル */
export interface Classifier<K extends string> {
  fit(x: readonly Record<K, number>[], t: readonly string[]): this;
  predict(x: readonly Record<K, number>[]): string[];
}

export interface Score {
  train: number;
  test: number;
}

export function evaluate<K extends string>(
  model: Classifier<K>,
  split: TrainTestSplit<Record<K, number>, string>,
): Score {
  model.fit(split.xTrain, split.tTrain);
  return {
    train: accuracy(model.predict(split.xTrain), split.tTrain),
    test: accuracy(model.predict(split.xTest), split.tTest),
  };
}
