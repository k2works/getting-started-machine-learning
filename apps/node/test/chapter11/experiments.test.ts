import { describe, expect, it } from "vitest";
import {
  meanAbsoluteError,
  rootMeanSquaredError,
} from "../../src/chapter11/metrics.ts";
import { evaluate } from "../../src/chapter11/experiments.ts";

/** どんな入力にも 0 を予測するテスト用のモデル */
const zeroModel = () => ({
  fit: () => {},
  predict: (x: readonly { feature: number }[]) => x.map(() => 0),
});

describe("evaluate", () => {
  it("評価関数ごとに交差検証のスコアの平均を名前を付けて返す", () => {
    const x = Array.from({ length: 10 }, (_, i) => ({ feature: i }));
    const t = x.map(() => 2);

    const scores = evaluate(zeroModel, x, t, {
      RMSE: rootMeanSquaredError,
      MAE: meanAbsoluteError,
    });

    expect(Object.keys(scores)).toEqual(["RMSE", "MAE"]);
    expect(scores).toEqual({ RMSE: 2, MAE: 2 });
  });
});
