import { createRandom } from "../../src/chapter02/random.ts";

/** 平均 0・標準偏差 1 の正規分布の乱数（ボックス＝ミュラー法） */
function gaussian(random: () => number): number {
  return (
    Math.sqrt(-2 * Math.log(1 - random())) * Math.cos(2 * Math.PI * random())
  );
}

/** 2 つの隠れた変数を 4 列に混ぜ、小さなノイズを加えた 40 行の架空のデータ */
export function mixedDataset(): number[][] {
  const random = createRandom(0);
  const mixing = [
    [2, 0.5],
    [0.3, 1],
    [1, -1],
    [0, 0.2],
  ];
  return Array.from({ length: 40 }, () => {
    const base = [gaussian(random), gaussian(random)];
    return mixing.map(
      (weights) =>
        weights.reduce((sum, w, i) => sum + w * (base[i] as number), 0) +
        gaussian(random) * 0.1,
    );
  });
}
