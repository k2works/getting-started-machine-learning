import { type Model, crossValidate, kFold } from "./cross-validation.ts";
import {
  loadCinema,
  loadSurvived,
  prepareCinema,
  prepareSurvived,
} from "./datasets.ts";
import {
  type Metric,
  accuracy,
  classificationMetric,
  f1Score,
  mean,
  meanAbsoluteError,
  precision,
  recall,
  rootMeanSquaredError,
} from "./metrics.ts";
import { DecisionTreeModel, LinearRegressionModel } from "./models.ts";

export const N_SPLITS = 5;
export const SEED = 0;
const TREE_DEPTH = 2;
const SURVIVED = "1";

export const SURVIVED_METRICS: Record<string, Metric<string>> = {
  正解率: accuracy,
  適合率: classificationMetric(precision, SURVIVED),
  再現率: classificationMetric(recall, SURVIVED),
  F値: classificationMetric(f1Score, SURVIVED),
};

export const CINEMA_METRICS: Record<string, Metric<number>> = {
  RMSE: rootMeanSquaredError,
  MAE: meanAbsoluteError,
};

export function evaluate<X, T>(
  makeModel: () => Model<X, T>,
  x: readonly X[],
  t: readonly T[],
  metrics: Record<string, Metric<T>>,
): Record<string, number> {
  const folds = kFold(x.length, N_SPLITS, SEED);
  return Object.fromEntries(
    Object.entries(metrics).map(([name, metric]) => [
      name,
      mean([...crossValidate(makeModel, x, t, folds, metric)]),
    ]),
  );
}

export function evaluateSurvived(csvFile: string): Record<string, number> {
  const { x, t } = prepareSurvived(loadSurvived(csvFile));
  return evaluate(
    () => new DecisionTreeModel(TREE_DEPTH),
    x,
    t,
    SURVIVED_METRICS,
  );
}

export function evaluateCinema(csvFile: string): Record<string, number> {
  const { x, t } = prepareCinema(loadCinema(csvFile));
  return evaluate(() => new LinearRegressionModel(), x, t, CINEMA_METRICS);
}
