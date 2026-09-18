import type { TrainTestSplit } from "../chapter02/iris-preprocessing.ts";
import { type FittedPipeline, predict } from "./pipeline.ts";
import type { Passenger } from "./survived-data.ts";

export interface Evaluation {
  trainAccuracy: number;
  testAccuracy: number;
  /** 実際の生存者のうち、生存と予測できた人数 */
  foundSurvivors: number;
  /** 実際の生存者の人数 */
  survivors: number;
}

function accuracy(
  predictions: readonly number[],
  labels: readonly number[],
): number {
  const correct = predictions.filter((p, i) => p === labels[i]).length;
  return correct / labels.length;
}

export function evaluate(
  pipeline: FittedPipeline,
  split: TrainTestSplit<Passenger, number>,
): Evaluation {
  const predictions = predict(pipeline, split.xTest);
  return {
    trainAccuracy: accuracy(predict(pipeline, split.xTrain), split.tTrain),
    testAccuracy: accuracy(predictions, split.tTest),
    foundSurvivors: predictions.filter(
      (p, i) => p === 1 && split.tTest[i] === 1,
    ).length,
    survivors: split.tTest.filter((t) => t === 1).length,
  };
}
