import { describe, expect, it } from "vitest";
import {
  meanAbsoluteError,
  r2Score,
  rootMeanSquaredError,
} from "../../src/chapter07/regression-metrics.ts";

describe("meanAbsoluteError", () => {
  const t = [3, 5, 7];

  it("誤差の絶対値の平均を求める", () => {
    expect(meanAbsoluteError(t, [2, 5, 9])).toBeCloseTo(1, 12);
  });

  it("予測が大きく外れるほど値が大きくなる", () => {
    expect(meanAbsoluteError(t, [1, 8, 7])).toBeCloseTo(5 / 3, 12);
  });

  it("実測値と予測値の件数が違えばエラーになる", () => {
    expect(() => meanAbsoluteError(t, [1])).toThrow(
      "実測値と予測値の件数が違います",
    );
  });
});

describe("rootMeanSquaredError", () => {
  it("誤差の 2 乗の平均の平方根を求める", () => {
    expect(rootMeanSquaredError([3, 5, 7], [2, 5, 9])).toBeCloseTo(
      Math.sqrt(5 / 3),
      12,
    );
  });
});

describe("r2Score", () => {
  const t = [3, 5, 7];

  it("予測がすべて正解なら 1 になる", () => {
    expect(r2Score(t, [3, 5, 7])).toBeCloseTo(1, 12);
  });

  it("平均値を予測し続けるモデルより良い分だけ 1 に近づく", () => {
    expect(r2Score(t, [2, 5, 9])).toBeCloseTo(1 - 5 / 8, 12);
  });
});
