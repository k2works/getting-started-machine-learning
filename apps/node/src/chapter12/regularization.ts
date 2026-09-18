import { Matrix, solve } from "ml-matrix";

export interface RegularizedModel {
  readonly coefficients: readonly number[];
  readonly intercept: number;
}

export function predict(
  model: RegularizedModel,
  x: readonly (readonly number[])[],
): number[] {
  return x.map((row) =>
    row.reduce(
      (sum, value, i) => sum + value * (model.coefficients[i] ?? 0),
      model.intercept,
    ),
  );
}

/** 決定係数（R²）。1 - 残差の二乗和 / 正解の平均からの偏差の二乗和 */
export function r2Score(t: readonly number[], y: readonly number[]): number {
  const mean = average(t);
  const residual = t.reduce(
    (sum, value, i) => sum + (value - (y[i] ?? NaN)) ** 2,
    0,
  );
  const total = t.reduce((sum, value) => sum + (value - mean) ** 2, 0);
  return 1 - residual / total;
}

function average(values: readonly number[]): number {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

export interface ValidationSplit {
  readonly xTrain: readonly (readonly number[])[];
  readonly tTrain: readonly number[];
  readonly xValid: readonly (readonly number[])[];
  readonly tValid: readonly number[];
}

export interface Experiment {
  readonly alpha: number;
  readonly trainScore: number;
  readonly validationScore: number;
  readonly coefficientAbsSum: number;
}

export function runRidgeExperiments(
  split: ValidationSplit,
  alphas: readonly number[],
): readonly Experiment[] {
  return alphas.map((alpha) => {
    const model = fitRidge(split.xTrain, split.tTrain, alpha);
    return {
      alpha,
      trainScore: r2Score(split.tTrain, predict(model, split.xTrain)),
      validationScore: r2Score(split.tValid, predict(model, split.xValid)),
      coefficientAbsSum: model.coefficients.reduce(
        (sum, value) => sum + Math.abs(value),
        0,
      ),
    };
  });
}

export function bestExperiment(experiments: readonly Experiment[]): Experiment {
  return experiments.reduce((best, e) =>
    e.validationScore > best.validationScore ? e : best,
  );
}

export function zeroCoefficientNames(
  coefficients: readonly number[],
  featureNames: readonly string[],
): string[] {
  return featureNames.filter((_, i) => coefficients[i] === 0);
}

export function fitRidge(
  x: readonly (readonly number[])[],
  t: readonly number[],
  alpha: number,
): RegularizedModel {
  const xMatrix = new Matrix(x.map((row) => [...row]));
  const xMeans = xMatrix.mean("column");
  const tMean = average(t);
  const xc = xMatrix.clone().subRowVector(xMeans);
  const tc = Matrix.columnVector(t.map((value) => value - tMean));
  const xct = xc.transpose();
  const coefficients = solve(
    xct.mmul(xc).add(Matrix.eye(xMatrix.columns).mul(alpha)),
    xct.mmul(tc),
  ).to1DArray();
  const intercept =
    tMean -
    xMeans.reduce((sum, mean, i) => sum + mean * (coefficients[i] ?? 0), 0);
  return { coefficients, intercept };
}
