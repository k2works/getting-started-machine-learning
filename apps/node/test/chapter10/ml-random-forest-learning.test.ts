import { RandomForestClassifier } from "ml-random-forest";
import { describe, expect, it } from "vitest";

// ml-random-forest 2.1.0 の振る舞いを確かめる学習用テスト
const x = [
  [0.5, 0.1],
  [0.3, 0.12],
  [0.6, 0.14],
  [0.4, 0.16],
  [0.5, 0.18],
  [0.3, 0.8],
  [0.6, 0.82],
  [0.4, 0.84],
  [0.5, 0.86],
  [0.3, 0.88],
];
const y = [0, 0, 0, 0, 0, 1, 1, 1, 1, 1];

describe("ml-random-forest の RandomForestClassifier", () => {
  it("木が少ないと、袋外の予測が空の行があり学習に失敗する", () => {
    const forest = new RandomForestClassifier({ nEstimators: 2, seed: 0 });

    expect(() => forest.train(x, y)).toThrow("input must not be empty");
  });

  it("袋外の予測を計算しなければ木が少なくても学習できる", () => {
    const forest = new RandomForestClassifier({
      nEstimators: 2,
      seed: 0,
      noOOB: true,
    });

    forest.train(x, y);

    expect(forest.predict([[0.4, 0.13]])).toEqual([0]);
  });

  it("maxFeatures の 1 は 1 個ではなく、すべての特徴量（100%）を表す", () => {
    const forest = new RandomForestClassifier({
      nEstimators: 5,
      maxFeatures: 1,
      seed: 0,
      noOOB: true,
    });

    forest.train(x, y);

    expect(forest.indexes?.every((used) => used.length === 2)).toBe(true);
  });

  it("既定では特徴量を重複を許して選ぶので、同じ列を 2 回使う木がある", () => {
    const forest = new RandomForestClassifier({
      nEstimators: 20,
      maxFeatures: 2,
      seed: 0,
      noOOB: true,
    });

    forest.train(x, y);

    expect(
      forest.indexes?.some((used) => new Set(used).size < used.length),
    ).toBe(true);
  });
});
