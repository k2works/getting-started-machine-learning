import { describe, expect, it } from "vitest";
import {
  type Matrix,
  multiply,
  solve,
  transpose,
} from "../../src/chapter07/matrix.ts";

describe("multiply", () => {
  it("行列の積を求める", () => {
    const a = [
      [1, 2],
      [3, 4],
    ];
    const b = [
      [5, 6],
      [7, 8],
    ];

    expect(multiply(a, b)).toEqual([
      [19, 22],
      [43, 50],
    ]);
  });

  it("行数と列数が違う行列の積を求める", () => {
    const a = [
      [1, 2, 3],
      [4, 5, 6],
    ];
    const b = [[1], [0], [2]];

    expect(multiply(a, b)).toEqual([[7], [16]]);
  });

  it("左の列数と右の行数が違えば積を求められない", () => {
    const a = [[1, 2]];

    expect(() => multiply(a, a)).toThrow(
      "左の行列の列数 2 と右の行列の行数 1 が違います",
    );
  });
});

describe("transpose", () => {
  it("行と列を入れ替える", () => {
    const a = [
      [1, 2, 3],
      [4, 5, 6],
    ];

    expect(transpose(a)).toEqual([
      [1, 4],
      [2, 5],
      [3, 6],
    ]);
  });
});

function expectMatrixCloseTo(actual: Matrix, expected: Matrix): void {
  expect(actual).toEqual(
    expected.map((row) => row.map((value) => expect.closeTo(value, 9))),
  );
}

describe("solve", () => {
  it("連立方程式の解を求める", () => {
    const a = [
      [2, 1],
      [1, 3],
    ];
    const b = [[3], [5]];

    expectMatrixCloseTo(solve(a, b), [[0.8], [1.4]]);
  });

  it("3 元の連立方程式の解を求める", () => {
    const a = [
      [4, 1, 2],
      [1, 3, 0],
      [2, 0, 5],
    ];
    const b = multiply(a, [[1], [-2], [3]]);

    expectMatrixCloseTo(solve(a, b), [[1], [-2], [3]]);
  });

  it("対角成分が 0 でも行を入れ替えて解を求める", () => {
    const a = [
      [0, 1],
      [1, 0],
    ];
    const b = [[2], [3]];

    expectMatrixCloseTo(solve(a, b), [[3], [2]]);
  });
});
