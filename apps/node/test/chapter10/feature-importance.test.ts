import { describe, expect, it } from "vitest";
import { DecisionTree, type Tree } from "../../src/chapter03/decision-tree.ts";
import {
  forestImportances,
  treeImportances,
} from "../../src/chapter10/feature-importance.ts";
import { RandomForest } from "../../src/chapter10/random-forest.ts";

function fitTree<K extends string>(
  x: readonly Record<K, number>[],
  t: readonly string[],
): Tree<K> {
  const tree = new DecisionTree<K>().fit(x, t).tree;
  if (tree === null) {
    throw new Error("決定木を学習できませんでした");
  }
  return tree;
}

describe("treeImportances", () => {
  it("分割しない木はすべての特徴量の重要度が 0", () => {
    const x = [
      { がく片幅: 0.3, 花弁幅: 0.1 },
      { がく片幅: 0.5, 花弁幅: 0.2 },
    ];
    const t = ["setosa", "setosa"];

    expect(treeImportances({ kind: "leaf", label: "setosa" }, x, t)).toEqual({
      がく片幅: 0,
      花弁幅: 0,
    });
  });

  it("1 回だけ分割する木は分割に使った特徴量の重要度が 1", () => {
    const x = [
      { がく片幅: 0.3, 花弁幅: 0.1 },
      { がく片幅: 0.5, 花弁幅: 0.2 },
      { がく片幅: 0.4, 花弁幅: 0.8 },
      { がく片幅: 0.6, 花弁幅: 0.9 },
    ];
    const t = ["setosa", "setosa", "virginica", "virginica"];
    expect(treeImportances(fitTree(x, t), x, t)).toEqual({
      がく片幅: 0,
      花弁幅: 1,
    });
  });

  it("分割で減った不純度を件数で重み付けして割合にする", () => {
    const x = [
      { 花弁長さ: 0.1, 花弁幅: 0.1 },
      { 花弁長さ: 0.2, 花弁幅: 0.1 },
      { 花弁長さ: 0.3, 花弁幅: 0.1 },
      { 花弁長さ: 0.8, 花弁幅: 0.2 },
      { 花弁長さ: 0.7, 花弁幅: 0.9 },
      { 花弁長さ: 0.9, 花弁幅: 0.9 },
    ];
    const t = [
      "setosa",
      "setosa",
      "setosa",
      "versicolor",
      "virginica",
      "virginica",
    ];
    const importances = treeImportances(fitTree(x, t), x, t);

    expect(importances.花弁長さ).toBeCloseTo(7 / 11, 12);
    expect(importances.花弁幅).toBeCloseTo(4 / 11, 12);
  });
});

describe("forestImportances", () => {
  it("木が 1 本なら学習に使った行でのその木の重要度と一致する", () => {
    const sepalWidths = [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4];
    const petalWidths = [0.1, 0.12, 0.14, 0.16, 0.8, 0.82, 0.84, 0.86];
    const x = sepalWidths.map((sepalWidth, i) => ({
      がく片幅: sepalWidth,
      花弁幅: petalWidths[i] as number,
    }));
    const t = [...Array(4).fill("setosa"), ...Array(4).fill("virginica")];
    const forest = new RandomForest({
      nEstimators: 1,
      maxFeatures: 2,
      seed: 0,
    }).fit(x, t);
    const [fitted] = forest.trees;
    if (fitted?.model.tree == null) {
      throw new Error("決定木を学習できませんでした");
    }

    const expected = treeImportances(
      fitted.model.tree,
      fitted.rows.map((row) => x[row] as (typeof x)[number]),
      fitted.rows.map((row) => t[row] as string),
    );

    expect(forestImportances(forest, x, t)).toEqual(expected);
  });
});
