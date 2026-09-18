import MultivariateLinearRegression from "ml-regression-multivariate-linear";
import type { LinearModel } from "./cinema-regression.ts";

/** ml-regression-multivariate-linear で学習し、自作と同じ LinearModel の形で返す */
export function trainMlLinearRegression<K extends string>(
  x: readonly Record<K, number>[],
  t: readonly number[],
): LinearModel<K> {
  const features = Object.keys(x[0] ?? {}) as K[];
  const regression = new MultivariateLinearRegression(
    x.map((row) => features.map((name) => row[name])),
    t.map((value) => [value]),
  );
  // weights は特徴量の係数の後ろに切片が並ぶ（1 列の行列）
  const weights = regression.weights.map(([w = Number.NaN]) => w);
  return {
    intercept: weights[features.length] ?? Number.NaN,
    coefficients: Object.fromEntries(
      features.map((name, i) => [name, weights[i] ?? Number.NaN]),
    ) as Record<K, number>,
  };
}
