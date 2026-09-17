import { describe, expect, it } from "vitest";
import { createRandom, shuffle } from "../../src/chapter02/random.ts";

function take(random: () => number, count: number): number[] {
  return Array.from({ length: count }, () => random());
}

describe("createRandom", () => {
  it("mulberry32 の参照実装と同じ値を返す", () => {
    expect(take(createRandom(0), 3)).toEqual([
      0.26642920868471265, 0.0003297457005828619, 0.2232720274478197,
    ]);
  });

  it("同じシードなら同じ乱数列を返す", () => {
    expect(take(createRandom(42), 5)).toEqual(take(createRandom(42), 5));
  });

  it("シードが違えば違う乱数列を返す", () => {
    expect(take(createRandom(0), 5)).not.toEqual(take(createRandom(1), 5));
  });

  it("0 以上 1 未満の値を返す", () => {
    const values = take(createRandom(7), 1000);

    expect(values.every((value) => value >= 0 && value < 1)).toBe(true);
  });
});

describe("shuffle", () => {
  it("要素を失わずに並べ替えた新しい配列を返す", () => {
    const items = [0, 1, 2, 3, 4, 5, 6, 7, 8, 9];

    const shuffled = shuffle(items, createRandom(0));

    expect(shuffled.toSorted((a, b) => a - b)).toEqual(items);
    expect(shuffled).not.toEqual(items);
    expect(items).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
  });
});
