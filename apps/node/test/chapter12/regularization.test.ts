import MultivariateLinearRegression from "ml-regression-multivariate-linear";
import { describe, expect, it } from "vitest";
import {
  type Experiment,
  bestExperiment,
  fitRidge,
  predict,
  r2Score,
  runRidgeExperiments,
  zeroCoefficientNames,
} from "../../src/chapter12/regularization.ts";
import { randomDataset } from "./random-dataset.ts";

describe("fitRidge", () => {
  it("alpha が 0 なら最小二乗法と同じ係数と切片になる", () => {
    const x = [[1], [2], [3]];
    const t = [3, 5, 7];

    const model = fitRidge(x, t, 0);

    expect(model.coefficients).toEqual([expect.closeTo(2, 12)]);
    expect(model.intercept).toBeCloseTo(1, 12);
  });

  it("特徴量が 2 つでも係数と切片を求める", () => {
    const x = [
      [1, 0],
      [0, 1],
      [1, 1],
      [2, 1],
    ];
    const t = x.map(([x1 = 0, x2 = 0]) => 3 * x1 - 1 * x2 + 4);

    const model = fitRidge(x, t, 0);

    expect(model.coefficients).toEqual([
      expect.closeTo(3, 9),
      expect.closeTo(-1, 9),
    ]);
    expect(model.intercept).toBeCloseTo(4, 9);
  });

  it("alpha を大きくすると係数の絶対値の合計が小さくなる", () => {
    const { x, t } = randomDataset([1.5, -2, 0.5, 3], 30, 0.5);

    const weak = fitRidge(x, t, 0.1);
    const strong = fitRidge(x, t, 100);

    expect(absSum(strong.coefficients)).toBeLessThan(absSum(weak.coefficients));
  });

  it("alpha が 0 なら ml-regression-multivariate-linear と同じ係数と切片になる", () => {
    const { x, t } = randomDataset([1.5, -2, 0.5, 3], 30, 0.5);

    const model = fitRidge(x, t, 0);
    const weights = new MultivariateLinearRegression(
      x,
      t.map((value) => [value]),
    ).weights.map(([w = NaN]) => w);

    expect(model.coefficients).toEqual(
      weights.slice(0, -1).map((w) => expect.closeTo(w, 9)),
    );
    expect(model.intercept).toBeCloseTo(weights.at(-1) ?? NaN, 9);
  });
});

describe("predict", () => {
  it("係数と切片から予測値を計算する", () => {
    const model = { coefficients: [3, -1], intercept: 4 };

    expect(
      predict(model, [
        [1, 2],
        [0, 0],
      ]),
    ).toEqual([5, 4]);
  });
});

describe("r2Score", () => {
  it("予測がすべて正解なら 1、正解の平均を予測するなら 0", () => {
    const t = [1, 2, 3, 6];

    expect(r2Score(t, t)).toBe(1);
    expect(r2Score(t, [3, 3, 3, 3])).toBe(0);
  });
});

describe("runRidgeExperiments", () => {
  const { x, t } = randomDataset([1.5, -2, 0.5, 3], 30, 0.5);
  const [xTrain, xValid, tTrain, tValid] = [
    x.slice(0, 20),
    x.slice(20),
    t.slice(0, 20),
    t.slice(20),
  ];

  it("正則化の強さごとに 1 件ずつ実験結果を記録する", () => {
    const experiments = runRidgeExperiments(
      { xTrain, tTrain, xValid, tValid },
      [0.1, 1, 10],
    );

    expect(experiments.map((e) => e.alpha)).toEqual([0.1, 1, 10]);
  });

  it("スプレッド構文で一部を変えた実験結果を作っても元の実験結果は変わらない", () => {
    const original = runRidgeExperiments(
      { xTrain, tTrain, xValid, tValid },
      [1],
    )[0] as Experiment;

    const changed = { ...original, alpha: 2 };

    expect(original.alpha).toBe(1);
    expect(changed.alpha).toBe(2);
    expect(changed.validationScore).toBe(original.validationScore);
  });
});

function experiment(alpha: number, validationScore: number): Experiment {
  return { alpha, trainScore: 0.9, validationScore, coefficientAbsSum: 1 };
}

describe("bestExperiment", () => {
  it("検証データの決定係数が最も高い実験を選ぶ", () => {
    const experiments = [experiment(0.1, 0.7), experiment(1, 0.6)];

    expect(bestExperiment(experiments).alpha).toBe(0.1);
  });

  it("最も高い実験が途中にあってもそれを選ぶ", () => {
    const experiments = [
      experiment(0.1, 0.5),
      experiment(1, 0.8),
      experiment(10, 0.6),
    ];

    expect(bestExperiment(experiments).alpha).toBe(1);
  });
});

describe("zeroCoefficientNames", () => {
  it("0 になった係数の特徴量名を返す", () => {
    expect(zeroCoefficientNames([0, 1.5, 0], ["RM", "LSTAT", "RM^2"])).toEqual([
      "RM",
      "RM^2",
    ]);
  });
});

function absSum(values: readonly number[]): number {
  return values.reduce((sum, value) => sum + Math.abs(value), 0);
}
