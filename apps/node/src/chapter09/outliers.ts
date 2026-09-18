import type { TrainTestSplit } from "../chapter02/iris-preprocessing.ts";

export function quantile(values: readonly number[], q: number): number {
  const sorted = values.toSorted((a, b) => a - b);
  const position = (sorted.length - 1) * q;
  const lower = sorted[Math.floor(position)] as number;
  const upper = sorted[Math.ceil(position)] as number;
  return lower + (upper - lower) * (position - Math.floor(position));
}

export function iqrOutliers(values: readonly number[], k = 1.5): boolean[] {
  const q1 = quantile(values, 0.25);
  const q3 = quantile(values, 0.75);
  const iqr = q3 - q1;
  return values.map((value) => value < q1 - k * iqr || value > q3 + k * iqr);
}

export function removeTargetOutliers<X>(
  split: TrainTestSplit<X, number>,
): TrainTestSplit<X, number> {
  const outliers = iqrOutliers(split.tTrain);
  return {
    ...split,
    xTrain: split.xTrain.filter((_, i) => !outliers[i]),
    tTrain: split.tTrain.filter((_, i) => !outliers[i]),
  };
}
