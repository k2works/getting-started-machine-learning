import { describe, expect, it } from "vitest";
import { DecisionTree } from "../../src/chapter03/decision-tree.ts";
import {
  DecisionTreeModel,
  LinearRegressionModel,
} from "../../src/chapter11/models.ts";

describe("DecisionTreeModel", () => {
  it("第 3 章の決定木と同じ予測をする", () => {
    const x = [0.1, 0.2, 0.3, 0.6, 0.7, 0.9].map((value) => ({
      feature: value,
    }));
    const t = ["0", "0", "1", "1", "0", "1"];
    const newX = [0.15, 0.35, 0.8].map((value) => ({ feature: value }));

    const model = new DecisionTreeModel(1);
    model.fit(x, t);

    expect(model.predict(newX)).toEqual(
      new DecisionTree({ maxDepth: 1 }).fit(x, t).predict(newX),
    );
  });
});

describe("LinearRegressionModel", () => {
  it("切片と係数から予測する", () => {
    // t = 1 + 2 * a - b がちょうど成り立つデータ
    const x = [
      { a: 1, b: 0 },
      { a: 2, b: 1 },
      { a: 3, b: 5 },
      { a: 4, b: 2 },
    ];
    const t = [3, 4, 2, 7];

    const model = new LinearRegressionModel();
    model.fit(x, t);

    const [predicted] = model.predict([{ a: 10, b: 3 }]);
    expect(predicted).toBeCloseTo(18, 9);
  });

  it("学習する前に予測するとエラーになる", () => {
    expect(() => new LinearRegressionModel().predict([{ a: 1 }])).toThrow(
      "fit で学習してから predict を呼んでください",
    );
  });
});
