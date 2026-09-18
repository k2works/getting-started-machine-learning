function residuals(t: readonly number[], y: readonly number[]): number[] {
  if (t.length !== y.length) {
    throw new Error("実測値と予測値の件数が違います");
  }
  return t.map((actual, i) => actual - (y[i] as number));
}

function mean(values: readonly number[]): number {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

export function meanAbsoluteError(
  t: readonly number[],
  y: readonly number[],
): number {
  return mean(residuals(t, y).map(Math.abs));
}

export function rootMeanSquaredError(
  t: readonly number[],
  y: readonly number[],
): number {
  return Math.sqrt(mean(residuals(t, y).map((r) => r * r)));
}

function sumOfSquares(values: readonly number[]): number {
  return values.reduce((sum, value) => sum + value * value, 0);
}

export function r2Score(t: readonly number[], y: readonly number[]): number {
  const average = mean(t);
  const residual = sumOfSquares(residuals(t, y));
  const total = sumOfSquares(t.map((value) => value - average));
  return 1 - residual / total;
}
