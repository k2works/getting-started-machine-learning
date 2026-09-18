import { LassoRegression } from "ml-regression-lasso";
import { describe, expect, it } from "vitest";
import { createRandom } from "../../src/chapter02/random.ts";
import { fitLasso } from "../../src/chapter12/lasso.ts";
import {
  fitRidge,
  zeroCoefficientNames,
} from "../../src/chapter12/regularization.ts";
import { randomDataset } from "./random-dataset.ts";

describe("fitLasso（ml-regression-lasso の学習用テスト）", () => {
  it("lambda が 0 なら最小二乗法（alpha が 0 のリッジ回帰）と同じ係数と切片になる", () => {
    const { x, t } = randomDataset([1.5, -2, 0.5, 3], 30, 0.5);

    const model = fitLasso(x, t, 0);
    const expected = fitRidge(x, t, 0);

    expect(model.coefficients).toEqual(
      expected.coefficients.map((c) => expect.closeTo(c, 6)),
    );
    expect(model.intercept).toBeCloseTo(expected.intercept, 6);
  });

  describe("特徴量が 1 つなら、lambda は相関係数の尺度で係数を縮める", () => {
    // x と t の相関係数は 0.6、どちらも標準偏差は同じ
    const x = [[1], [2], [3], [4]];
    const t = [2, 1, 4, 3];

    it("lambda が 0 なら係数は相関係数と同じ 0.6", () => {
      expect(fitLasso(x, t, 0).coefficients).toEqual([expect.closeTo(0.6, 12)]);
    });

    it("lambda が 0.2 なら係数は 0.6 - 0.2 = 0.4", () => {
      const model = fitLasso(x, t, 0.2);

      expect(model.coefficients).toEqual([expect.closeTo(0.4, 12)]);
      expect(model.intercept).toBeCloseTo(1.5, 12);
    });

    it("lambda が相関係数より大きければ係数はちょうど 0", () => {
      expect(fitLasso(x, t, 0.7).coefficients).toEqual([0]);
    });
  });

  it("予測に役立たない特徴量の係数がちょうど 0 になる", () => {
    const { x, t } = randomDataset([3, -2, 0, 0], 50, 0.1);

    const model = fitLasso(x, t, 0.2);

    expect(
      zeroCoefficientNames(model.coefficients, [
        "x1",
        "x2",
        "noise1",
        "noise2",
      ]),
    ).toEqual(["noise1", "noise2"]);
  });
});

/** 2 列がほぼ同じ値（強い相関）を持つデータ。座標降下法の収束が遅くなる */
function collinearDataset(): { x: number[][]; t: number[] } {
  const random = createRandom(0);
  const x = Array.from({ length: 30 }, () => {
    const a = random() * 2 - 1;
    return [a, a + 0.1 * (random() * 2 - 1)];
  });
  const t = x.map(([a = 0, b = 0]) => a + b + 0.1 * (random() * 2 - 1));
  return { x, t };
}

describe("ml-regression-lasso の収束", () => {
  it("既定の反復回数（200 回）では収束せず、例外にならずに converged が false になる", () => {
    const { x, t } = collinearDataset();

    const lasso = new LassoRegression(
      x,
      t.map((value) => [value]),
      { lambda: 0 },
    );

    expect(lasso.converged).toBe(false);
  });

  it("強い相関のある特徴量でも、lambda が 0 なら最小二乗法と同じ係数になる", () => {
    const { x, t } = collinearDataset();

    const model = fitLasso(x, t, 0);
    const expected = fitRidge(x, t, 0);

    expect(model.coefficients).toEqual(
      expected.coefficients.map((c) => expect.closeTo(c, 6)),
    );
  });

  it("反復の上限までに収束しなければ例外にする", () => {
    const { x, t } = collinearDataset();

    expect(() => fitLasso(x, t, 0, { maxIterations: 200 })).toThrow(
      "ラッソ回帰の座標降下法が 200 回の反復で収束しませんでした",
    );
  });
});
