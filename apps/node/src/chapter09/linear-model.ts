import { CholeskyDecomposition, Matrix } from "ml-matrix";
import { mean } from "./statistics.ts";

export class LinearModel {
  readonly intercept: number;
  readonly weights: number[];

  constructor(intercept: number, weights: number[]) {
    this.intercept = intercept;
    this.weights = weights;
  }

  predict(rows: readonly (readonly number[])[]): number[] {
    return rows.map((row) =>
      row.reduce(
        (sum, value, i) => sum + (this.weights[i] as number) * value,
        this.intercept,
      ),
    );
  }
}

export function fitLinearRegression(
  rows: readonly (readonly number[])[],
  t: readonly number[],
): LinearModel {
  const design = new Matrix(rows.map((row) => [1, ...row]));
  const transposed = design.transpose();
  const cholesky = new CholeskyDecomposition(transposed.mmul(design));
  if (!cholesky.isPositiveDefinite()) {
    throw new Error("特徴量の列が互いに独立でないため、正規方程式を解けません");
  }
  const [intercept = 0, ...weights] = cholesky
    .solve(transposed.mmul(Matrix.columnVector([...t])))
    .to1DArray();
  return new LinearModel(intercept, weights);
}

export function rSquared(
  actual: readonly number[],
  predicted: readonly number[],
): number {
  const average = mean(actual);
  const residual = actual.reduce(
    (sum, value, i) => sum + (value - (predicted[i] as number)) ** 2,
    0,
  );
  const total = actual.reduce((sum, value) => sum + (value - average) ** 2, 0);
  return 1 - residual / total;
}
