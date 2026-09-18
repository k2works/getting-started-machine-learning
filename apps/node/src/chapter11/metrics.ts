export interface ConfusionMatrix {
  tp: number;
  fp: number;
  fn: number;
  tn: number;
}

export type Metric<T> = (
  actual: readonly T[],
  predicted: readonly T[],
) => number;

function zipPredictions<T>(
  actual: readonly T[],
  predicted: readonly T[],
): [T, T][] {
  if (actual.length !== predicted.length) {
    throw new Error(
      `正解と予測の件数が違います（正解 ${actual.length} 件、予測 ${predicted.length} 件）`,
    );
  }
  return actual.map((a, i) => [a, predicted[i] as T]);
}

export function mean(values: readonly number[]): number {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

export function confusionMatrix<T>(
  actual: readonly T[],
  predicted: readonly T[],
  positive: T,
): ConfusionMatrix {
  const pairs = zipPredictions(actual, predicted).map(([a, p]) => ({
    isPositive: a === positive,
    predictedPositive: p === positive,
  }));
  const count = (isPositive: boolean, predictedPositive: boolean) =>
    pairs.filter(
      (pair) =>
        pair.isPositive === isPositive &&
        pair.predictedPositive === predictedPositive,
    ).length;
  return {
    tp: count(true, true),
    fp: count(false, true),
    fn: count(true, false),
    tn: count(false, false),
  };
}

function ratio(numerator: number, denominator: number): number {
  return denominator === 0 ? 0 : numerator / denominator;
}

export function precision(cm: ConfusionMatrix): number {
  return ratio(cm.tp, cm.tp + cm.fp);
}

export function recall(cm: ConfusionMatrix): number {
  return ratio(cm.tp, cm.tp + cm.fn);
}

export function f1Score(cm: ConfusionMatrix): number {
  const p = precision(cm);
  const r = recall(cm);
  return ratio(2 * p * r, p + r);
}

export function meanSquaredError(
  actual: readonly number[],
  predicted: readonly number[],
): number {
  return mean(zipPredictions(actual, predicted).map(([a, p]) => (p - a) ** 2));
}

export function rootMeanSquaredError(
  actual: readonly number[],
  predicted: readonly number[],
): number {
  return Math.sqrt(meanSquaredError(actual, predicted));
}

export function meanAbsoluteError(
  actual: readonly number[],
  predicted: readonly number[],
): number {
  return mean(
    zipPredictions(actual, predicted).map(([a, p]) => Math.abs(p - a)),
  );
}

export function accuracy<T>(
  actual: readonly T[],
  predicted: readonly T[],
): number {
  const pairs = zipPredictions(actual, predicted);
  return pairs.filter(([a, p]) => a === p).length / pairs.length;
}

export function classificationMetric<T>(
  score: (cm: ConfusionMatrix) => number,
  positive: T,
): Metric<T> {
  return (actual, predicted) =>
    score(confusionMatrix(actual, predicted, positive));
}
