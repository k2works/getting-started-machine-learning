import { Matrix as MlMatrix, solve as mlSolve } from "ml-matrix";
import MultivariateLinearRegression from "ml-regression-multivariate-linear";
import { describe, expect, it } from "vitest";
import { createRandom } from "../../src/chapter02/random.ts";
import {
  fitLinearRegression,
  predict,
} from "../../src/chapter07/cinema-regression.ts";
import { solve } from "../../src/chapter07/matrix.ts";
import { trainMlLinearRegression } from "../../src/chapter07/ml-regression-adapter.ts";

type Row = Record<"a" | "b" | "c", number>;

/** t = 4 + 1.5a - 0.5b + 2c に -0.5〜0.5 の一様な乱数を足した 30 件 */
function noisyDataset(): { x: Row[]; t: number[] } {
  const random = createRandom(0);
  const x = Array.from({ length: 30 }, () => ({
    a: random() * 10,
    b: random() * 10,
    c: random() * 10,
  }));
  const t = x.map(
    ({ a, b, c }) => 4 + 1.5 * a - 0.5 * b + 2 * c + (random() - 0.5),
  );
  return { x, t };
}

describe("ml-regression-multivariate-linear（学習用テスト）", () => {
  it("weights は係数の後ろに切片を並べる", () => {
    const x = [[0], [1], [2], [3]];
    const t = [[1], [3], [5], [7]];

    const regression = new MultivariateLinearRegression(x, t);

    expect(regression.weights).toEqual([
      [expect.closeTo(2, 9)],
      [expect.closeTo(1, 9)],
    ]);
  });

  it("predict は自作の predict と同じ予測値を返す", () => {
    const { x, t } = noisyDataset();
    const rows = x.map(({ a, b, c }) => [a, b, c]);

    const regression = new MultivariateLinearRegression(
      rows,
      t.map((value) => [value]),
    );

    expect(regression.predict(rows).map(([y]) => y)).toEqual(
      predict(fitLinearRegression(x, t), x).map((y) => expect.closeTo(y, 9)),
    );
  });
});

describe("ml-matrix の solve（学習用テスト）", () => {
  it("対角成分が 0 の連立方程式も自作の solve と同じ解を返す", () => {
    const a = [
      [0, 1],
      [1, 0],
    ];
    const b = [[2], [3]];

    const library = mlSolve(new MlMatrix(a), new MlMatrix(b)).to2DArray();

    expect(library).toEqual(
      solve(a, b).map((row) => row.map((value) => expect.closeTo(value, 9))),
    );
  });
});

describe("trainMlLinearRegression", () => {
  it("ライブラリで学習した係数を自作の線形回帰と同じ形で返す", () => {
    const { x, t } = noisyDataset();

    const library = trainMlLinearRegression(x, t);
    const mine = fitLinearRegression(x, t);

    expect(library.intercept).toBeCloseTo(mine.intercept, 9);
    for (const name of ["a", "b", "c"] as const) {
      expect(library.coefficients[name]).toBeCloseTo(
        mine.coefficients[name],
        9,
      );
    }
  });
});
