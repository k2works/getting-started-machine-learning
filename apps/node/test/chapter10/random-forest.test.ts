import { describe, expect, it } from "vitest";
import { createRandom } from "../../src/chapter02/random.ts";
import {
  RandomForest,
  bootstrapSample,
  majorityVote,
} from "../../src/chapter10/random-forest.ts";

function twoSpecies() {
  const sepalWidths = [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4, 0.5, 0.3];
  const petalWidths = [
    0.1, 0.12, 0.14, 0.16, 0.18, 0.8, 0.82, 0.84, 0.86, 0.88,
  ];
  const x = sepalWidths.map((sepalWidth, i) => ({
    がく片幅: sepalWidth,
    花弁幅: petalWidths[i] as number,
  }));
  const t = [...Array(5).fill("setosa"), ...Array(5).fill("virginica")];
  return { x, t };
}

describe("majorityVote", () => {
  it("サンプルごとに最も多い予測を選ぶ", () => {
    const votes = [
      ["setosa", "virginica"],
      ["setosa", "virginica"],
      ["versicolor", "setosa"],
    ];

    expect(majorityVote(votes)).toEqual(["setosa", "virginica"]);
  });
});

describe("bootstrapSample", () => {
  it("元のデータと同じ件数の行番号を重複を許して選ぶ", () => {
    const rows = bootstrapSample(100, createRandom(0));

    expect(rows).toHaveLength(100);
    expect(
      rows.every((row) => Number.isInteger(row) && row >= 0 && row < 100),
    ).toBe(true);
    expect(new Set(rows).size).toBeLessThan(100);
  });

  it("同じシードなら同じ行を選ぶ", () => {
    expect(bootstrapSample(10, createRandom(42))).toEqual(
      bootstrapSample(10, createRandom(42)),
    );
  });
});

describe("RandomForest", () => {
  it("指定した数だけ第 3 章の決定木を学習する", () => {
    const { x, t } = twoSpecies();

    const model = new RandomForest({
      nEstimators: 5,
      maxFeatures: 1,
      seed: 0,
    }).fit(x, t);

    expect(model.trees).toHaveLength(5);
  });

  it("各決定木は指定した数の特徴量だけを使う", () => {
    const { x, t } = twoSpecies();

    const model = new RandomForest({
      nEstimators: 5,
      maxFeatures: 1,
      seed: 0,
    }).fit(x, t);

    expect(model.trees.every((tree) => tree.columns.length === 1)).toBe(true);
  });

  it("決定木の多数決で予測する", () => {
    const { x, t } = twoSpecies();

    const model = new RandomForest({
      nEstimators: 25,
      maxFeatures: 2,
      seed: 0,
    }).fit(x, t);

    const newX = [
      { がく片幅: 0.4, 花弁幅: 0.13 },
      { がく片幅: 0.4, 花弁幅: 0.83 },
    ];
    expect(model.predict(newX)).toEqual(["setosa", "virginica"]);
  });

  it("同じシードなら同じ特徴量を選び同じ予測になる", () => {
    const { x, t } = twoSpecies();

    const first = new RandomForest({
      nEstimators: 5,
      maxFeatures: 1,
      seed: 7,
    }).fit(x, t);
    const second = new RandomForest({
      nEstimators: 5,
      maxFeatures: 1,
      seed: 7,
    }).fit(x, t);

    expect(first.trees.map((tree) => tree.columns)).toEqual(
      second.trees.map((tree) => tree.columns),
    );
    expect(first.predict(x)).toEqual(second.predict(x));
  });

  it("学習する前に予測するとエラーになる", () => {
    expect(() => new RandomForest().predict([{ 花弁幅: 0.1 }])).toThrow(
      "fit で学習してから predict を呼んでください",
    );
  });
});
