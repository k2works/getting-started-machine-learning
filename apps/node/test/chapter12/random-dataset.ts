import { createRandom } from "../../src/chapter02/random.ts";

/** -1 以上 1 未満の一様乱数で作った特徴量と、その線形和に雑音を加えた正解 */
export function randomDataset(
  weights: readonly number[],
  size: number,
  noise: number,
  seed = 0,
): { x: number[][]; t: number[] } {
  const random = createRandom(seed);
  const uniform = () => random() * 2 - 1;
  const x = Array.from({ length: size }, () => weights.map(() => uniform()));
  const t = x.map(
    (row) =>
      row.reduce((sum, value, i) => sum + value * (weights[i] ?? 0), 0) +
      noise * uniform(),
  );
  return { x, t };
}
