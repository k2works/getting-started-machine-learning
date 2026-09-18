import { describe, expect, it } from "vitest";
import type { TrainTestSplit } from "../../src/chapter02/iris-preprocessing.ts";
import { DecisionTree } from "../../src/chapter03/decision-tree.ts";
import { type Classifier, evaluate } from "../../src/chapter10/classifier.ts";
import { LogisticRegression } from "../../src/chapter10/logistic-regression.ts";
import { RandomForest } from "../../src/chapter10/random-forest.ts";

type PetalWidth = Record<"花弁幅", number>;

class AlwaysSetosa implements Classifier<"花弁幅"> {
  fit(): this {
    return this;
  }

  predict(x: readonly PetalWidth[]): string[] {
    return x.map(() => "setosa");
  }
}

function smallSplit(): TrainTestSplit<PetalWidth, string> {
  return {
    xTrain: [
      { 花弁幅: 0.1 },
      { 花弁幅: 0.2 },
      { 花弁幅: 0.8 },
      { 花弁幅: 0.9 },
    ],
    xTest: [{ 花弁幅: 0.15 }, { 花弁幅: 0.25 }],
    tTrain: ["setosa", "setosa", "virginica", "virginica"],
    tTest: ["setosa", "setosa"],
  };
}

describe("evaluate", () => {
  it("学習させてから訓練データとテストデータの正解率を求める", () => {
    expect(evaluate(new AlwaysSetosa(), smallSplit())).toEqual({
      train: 0.5,
      test: 1,
    });
  });

  it("第 3 章の決定木と自作のモデルを同じ関数で評価できる", () => {
    const models: Classifier<"花弁幅">[] = [
      new DecisionTree({ maxDepth: 1 }),
      new LogisticRegression(),
      new RandomForest({ nEstimators: 5, maxFeatures: 1, seed: 0 }),
    ];

    const scores = models.map((model) => evaluate(model, smallSplit()));

    expect(scores).toEqual([
      { train: 1, test: 1 },
      { train: 1, test: 1 },
      { train: 1, test: 1 },
    ]);
  });
});
