import { EVD, Matrix } from "ml-matrix";
import { describe, expect, it } from "vitest";

function round(values: number[]): number[] {
  return values.map((value) => Math.round(value * 1e9) / 1e9);
}

describe("ml-matrix の EVD（学習用テスト）", () => {
  const symmetric = new Matrix([
    [2, 1],
    [1, 2],
  ]);

  it("対称行列の固有値を小さい順に返す", () => {
    const evd = new EVD(symmetric);

    expect(round(evd.realEigenvalues)).toEqual([1, 3]);
  });

  it("対角成分の並びに関係なく固有値を小さい順に並べ替える", () => {
    const diagonal = new Matrix([
      [1, 0, 0],
      [0, 5, 0],
      [0, 0, 3],
    ]);

    const evd = new EVD(diagonal);

    expect(round(evd.realEigenvalues)).toEqual([1, 3, 5]);
  });

  it("i 番目の固有ベクトルは固有ベクトル行列の i 列目で、行列を掛けると i 番目の固有値倍になる", () => {
    const evd = new EVD(symmetric);

    for (let i = 0; i < 2; i++) {
      const v = evd.eigenvectorMatrix.getColumnVector(i);
      const av = symmetric.mmul(v).to1DArray();
      const lambda = evd.realEigenvalues[i] as number;
      expect(av).toEqual(
        v.to1DArray().map((value) => expect.closeTo(lambda * value, 9)),
      );
    }
  });

  it("対称でない行列では、複素数の固有値の虚部を imaginaryEigenvalues に返す", () => {
    const rotation = new Matrix([
      [0, -1],
      [1, 0],
    ]);

    const evd = new EVD(rotation);

    expect(round(evd.imaginaryEigenvalues)).toEqual([1, -1]);
  });
});
