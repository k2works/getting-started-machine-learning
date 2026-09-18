import { LassoRegression } from "ml-regression-lasso";
import type { RegularizedModel } from "./regularization.ts";

/** 収束の判定値（1 回の反復での係数の変化の大きさ）。ml-regression-lasso の既定値は 1e-5 */
const TOLERANCE = 1e-10;

export interface LassoOptions {
  /** 反復の上限。ml-regression-lasso の既定値は 200 */
  maxIterations?: number;
}

/**
 * ml-regression-lasso でラッソ回帰を学習する。lambda は、特徴量と正解をそれぞれ
 * 標準化した尺度での L1 の罰則の強さ（特徴量が 1 つなら相関係数を lambda だけ 0 に近づける）
 */
export function fitLasso(
  x: readonly (readonly number[])[],
  t: readonly number[],
  lambda: number,
  { maxIterations = 100_000 }: LassoOptions = {},
): RegularizedModel {
  const lasso = new LassoRegression(
    x.map((row) => [...row]),
    t.map((value) => [value]),
    { lambda, tolerance: TOLERANCE, maxIter: maxIterations },
  );
  if (!lasso.converged) {
    throw new Error(
      `ラッソ回帰の座標降下法が ${maxIterations} 回の反復で収束しませんでした`,
    );
  }
  // weights は特徴量ごとの係数の行の後ろに、切片の行が並ぶ（出力が 1 列なので各行は 1 要素）
  const weights = (lasso.weights ?? []).map(([w = NaN]) => w);
  return {
    coefficients: weights.slice(0, -1),
    intercept: weights.at(-1) ?? NaN,
  };
}
