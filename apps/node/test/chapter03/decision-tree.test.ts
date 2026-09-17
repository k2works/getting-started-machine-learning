import { describe, expect, it } from "vitest";
import {
  DecisionTree,
  type Tree,
  bestSplit,
  formatTree,
  gini,
} from "../../src/chapter03/decision-tree.ts";

describe("gini", () => {
  it("1 種類のラベルだけならジニ不純度は 0", () => {
    expect(gini(["Iris-setosa", "Iris-setosa", "Iris-setosa"])).toBe(0);
  });

  it("2 種類のラベルが半分ずつならジニ不純度は 0.5", () => {
    expect(gini(["Iris-setosa", "Iris-virginica"])).toBe(0.5);
  });

  it("3 種類のラベルが同じ数ならジニ不純度は 3 分の 2", () => {
    const labels = ["Iris-setosa", "Iris-versicolor", "Iris-virginica"];

    expect(gini(labels)).toBeCloseTo(2 / 3, 12);
  });
});

describe("bestSplit", () => {
  it("ラベルを完全に分けられる境界を見つける", () => {
    const x = [
      { 花弁幅: 0.1 },
      { 花弁幅: 0.2 },
      { 花弁幅: 0.7 },
      { 花弁幅: 0.8 },
    ];
    const t = ["setosa", "setosa", "virginica", "virginica"];

    expect(bestSplit(x, t)).toEqual({
      feature: "花弁幅",
      threshold: expect.closeTo(0.45, 12),
      impurity: 0,
    });
  });

  it("複数の特徴量から不純度が最も小さくなる特徴量と境界を選ぶ", () => {
    const x = [
      { がく片長さ: 0.1, 花弁長さ: 0.2 },
      { がく片長さ: 0.3, 花弁長さ: 0.1 },
      { がく片長さ: 0.2, 花弁長さ: 0.9 },
      { がく片長さ: 0.4, 花弁長さ: 0.6 },
    ];
    const t = ["setosa", "setosa", "virginica", "virginica"];

    expect(bestSplit(x, t)).toEqual({
      feature: "花弁長さ",
      threshold: expect.closeTo(0.4, 12),
      impurity: 0,
    });
  });

  it("ラベルが 1 種類なら分割しない", () => {
    const x = [{ 花弁幅: 0.1 }, { 花弁幅: 0.2 }, { 花弁幅: 0.7 }];
    const t = ["setosa", "setosa", "setosa"];

    expect(bestSplit(x, t)).toBeNull();
  });
});

describe("DecisionTree", () => {
  it("1 種類のラベルだけを学習するとそのラベルを予測する", () => {
    const x = [{ 花弁幅: 0.1 }, { 花弁幅: 0.2 }];
    const t = ["setosa", "setosa"];

    const model = new DecisionTree().fit(x, t);

    expect(model.predict([{ 花弁幅: 0.15 }, { 花弁幅: 0.9 }])).toEqual([
      "setosa",
      "setosa",
    ]);
  });

  it("境界の左右で異なるラベルを予測する", () => {
    const x = [
      { 花弁幅: 0.1 },
      { 花弁幅: 0.2 },
      { 花弁幅: 0.7 },
      { 花弁幅: 0.8 },
    ];
    const t = ["setosa", "setosa", "virginica", "virginica"];

    const model = new DecisionTree().fit(x, t);

    expect(model.predict([{ 花弁幅: 0.15 }, { 花弁幅: 0.75 }])).toEqual([
      "setosa",
      "virginica",
    ]);
  });
});

function threeSpecies(): { x: { 花弁幅: number }[]; t: string[] } {
  const x = [0.1, 0.2, 0.3, 0.5, 0.6, 0.9].map((value) => ({ 花弁幅: value }));
  const t = [
    "setosa",
    "setosa",
    "setosa",
    "versicolor",
    "versicolor",
    "virginica",
  ];
  return { x, t };
}

describe("DecisionTree の深さの制限", () => {
  it("深さを制限しなければすべての訓練データを分け切る", () => {
    const { x, t } = threeSpecies();

    const model = new DecisionTree().fit(x, t);

    expect(model.predict(x)).toEqual(t);
  });

  it("深さを 1 に制限すると境界の先は多数派のラベルを予測する", () => {
    const { x, t } = threeSpecies();

    const model = new DecisionTree({ maxDepth: 1 }).fit(x, t);

    expect(model.predict([{ 花弁幅: 0.2 }, { 花弁幅: 0.95 }])).toEqual([
      "setosa",
      "versicolor",
    ]);
  });

  it("学習する前に予測するとエラーになる", () => {
    expect(() => new DecisionTree().predict([{ 花弁幅: 0.1 }])).toThrow(
      "fit で学習してから predict を呼んでください",
    );
  });
});

describe("formatTree", () => {
  it("葉だけの木はラベルを表示する", () => {
    expect(formatTree({ kind: "leaf", label: "setosa" })).toBe("setosa");
  });

  it("節は条件ごとに字下げして表示する", () => {
    const tree: Tree<"花弁幅" | "花弁長さ"> = {
      kind: "node",
      split: { feature: "花弁幅", threshold: 0.4, impurity: 0 },
      left: { kind: "leaf", label: "setosa" },
      right: {
        kind: "node",
        split: { feature: "花弁長さ", threshold: 0.75, impurity: 0 },
        left: { kind: "leaf", label: "versicolor" },
        right: { kind: "leaf", label: "virginica" },
      },
    };

    expect(formatTree(tree)).toBe(
      [
        "花弁幅 <= 0.4000",
        "  setosa",
        "花弁幅 > 0.4000",
        "  花弁長さ <= 0.7500",
        "    versicolor",
        "  花弁長さ > 0.7500",
        "    virginica",
      ].join("\n"),
    );
  });
});
