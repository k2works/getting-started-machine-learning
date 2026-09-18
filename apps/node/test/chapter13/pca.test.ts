import { Matrix } from "ml-matrix";
import { describe, expect, it } from "vitest";
import {
  type PcaModel,
  componentsNeeded,
  covarianceMatrix,
  fitPca,
  normalizeSigns,
  topLoadings,
  transform,
} from "../../src/chapter13/pca.ts";
import { mixedDataset } from "./mixed-dataset.ts";

function closeToMatrix(expected: number[][]): unknown[][] {
  return expected.map((row) => row.map((value) => expect.closeTo(value, 9)));
}

describe("covarianceMatrix", () => {
  it("2 列の分散と共分散を並べた行列を返す", () => {
    const x = [
      [1, 2],
      [3, 6],
      [5, 10],
    ];

    expect(covarianceMatrix(x)).toEqual(
      closeToMatrix([
        [4, 8],
        [8, 16],
      ]),
    );
  });

  it("3 列でも各列の分散と 2 列ずつの共分散を並べる", () => {
    const x = [
      [1, 2, 0],
      [3, 6, 1],
      [5, 10, 5],
    ];

    expect(covarianceMatrix(x)).toEqual(
      closeToMatrix([
        [4, 8, 5],
        [8, 16, 10],
        [5, 10, 7],
      ]),
    );
  });
});

function closeToList(expected: number[]): unknown[] {
  return expected.map((value) => expect.closeTo(value, 9));
}

describe("fitPca", () => {
  it("完全に相関する 2 列なら第 1 主成分だけで分散をすべて説明する", () => {
    const x = [
      [1, 2],
      [3, 6],
      [5, 10],
    ];

    const model = fitPca(x, 2);

    expect(model.components[0]).toEqual(
      closeToList([1 / Math.sqrt(5), 2 / Math.sqrt(5)]),
    );
    expect(model.explainedVarianceRatio).toEqual(closeToList([1, 0]));
  });

  it("主成分は寄与率の大きい順に指定した数だけ並ぶ", () => {
    const model = fitPca(mixedDataset(), 3);

    const ratios = model.explainedVarianceRatio;
    expect(ratios).toHaveLength(3);
    expect(ratios).toEqual([...ratios].sort((a, b) => b - a));
  });

  it("主成分の向きは絶対値が最大の要素が正になるようにそろえる", () => {
    const x = [
      [1, 2],
      [3, 6],
      [5, 10],
    ];

    const model = fitPca(x, 2);

    expect(model.components[1]).toEqual(
      closeToList([2 / Math.sqrt(5), -1 / Math.sqrt(5)]),
    );
  });

  it("主成分は長さ 1 で互いに直交する", () => {
    const model = fitPca(mixedDataset(), 3);

    const c = new Matrix(model.components);
    const gram = c.mmul(c.transpose()).to2DArray();

    expect(gram).toEqual(closeToMatrix(Matrix.eye(3).to2DArray()));
  });

  it("主成分は分散共分散行列に掛けると分散の倍数になる固有ベクトル", () => {
    const x = mixedDataset();

    const model = fitPca(x, 3);

    const covariance = new Matrix(covarianceMatrix(x));
    model.components.forEach((component, i) => {
      const variance = model.explainedVariance[i] as number;
      const projected = covariance.mmul(Matrix.columnVector(component));
      expect(projected.to1DArray()).toEqual(
        closeToList(component.map((value) => value * variance)),
      );
    });
  });
});

describe("normalizeSigns", () => {
  it("絶対値が最大の要素が正になるように主成分の向きをそろえる", () => {
    const components = [
      [0.6, -0.8],
      [-0.8, 0.6],
    ];

    expect(normalizeSigns(components)).toEqual(
      closeToMatrix([
        [-0.6, 0.8],
        [0.8, -0.6],
      ]),
    );
  });
});

describe("transform", () => {
  it("平均を引いてから主成分の向きに射影する", () => {
    const model: PcaModel = {
      mean: [1, 2],
      components: [[0.6, 0.8]],
      explainedVariance: [1],
      explainedVarianceRatio: [1],
    };

    const x = [
      [2, 3],
      [1, 2],
    ];

    expect(transform(model, x)).toEqual(closeToMatrix([[1.4], [0]]));
  });
});

describe("componentsNeeded", () => {
  it("累積寄与率がしきい値に届くまでの主成分の数を返す", () => {
    expect(componentsNeeded([0.5, 0.25, 0.25], 0.75)).toBe(2);
  });

  it("しきい値を上げると必要な主成分の数が増える", () => {
    expect(componentsNeeded([0.5, 0.25, 0.25], 0.8)).toBe(3);
  });
});

describe("topLoadings", () => {
  it("係数の絶対値が大きい順に列名と係数を返す", () => {
    const component = [0.1, -0.7, 0.5];

    expect(topLoadings(component, ["ZN", "DIS", "TAX"], 2)).toEqual([
      ["DIS", -0.7],
      ["TAX", 0.5],
    ]);
  });
});
