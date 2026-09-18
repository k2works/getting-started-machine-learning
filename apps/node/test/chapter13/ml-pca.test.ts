import { PCA } from "ml-pca";
import { describe, expect, it } from "vitest";
import { fitPca, normalizeSigns } from "../../src/chapter13/pca.ts";
import { mixedDataset } from "./mixed-dataset.ts";

function closeToMatrix(expected: number[][]): unknown[][] {
  return expected.map((row) => row.map((value) => expect.closeTo(value, 9)));
}

/** ml-pca の固有ベクトル行列は列が主成分なので、転置して 1 行を 1 つの主成分にする */
function componentsOf(pca: PCA): number[][] {
  return pca.getEigenvectors().transpose().to2DArray();
}

describe("ml-pca との突き合わせ", () => {
  const x = mixedDataset();
  const mine = fitPca(x, 4);
  const library = new PCA(x);

  it("寄与率が自作の主成分分析と一致する", () => {
    expect(library.getExplainedVariance()).toEqual(
      mine.explainedVarianceRatio.map((r) => expect.closeTo(r, 9)),
    );
  });

  it("固有値は n − 1 で割った分散で、自作の主成分の分散と一致する", () => {
    expect(library.getEigenvalues()).toEqual(
      mine.explainedVariance.map((v) => expect.closeTo(v, 9)),
    );
  });

  it("主成分の向きをそろえると、自作の主成分と一致する", () => {
    expect(normalizeSigns(componentsOf(library))).toEqual(
      closeToMatrix(mine.components),
    );
  });
});

describe("ml-pca の主成分の符号（学習用テスト）", () => {
  const x = mixedDataset();

  it("向きをそろえないと、第 3・第 4 主成分の符号が自作と逆になる", () => {
    const mine = fitPca(x, 4).components;
    const library = componentsOf(new PCA(x));

    const flipped = library.map((component, i) =>
      component.every(
        (value, j) => Math.abs(value + (mine[i]?.[j] as number)) < 1e-9,
      ),
    );

    expect(flipped).toEqual([false, false, true, true]);
  });

  it("計算方法（SVD と分散共分散行列）を変えると、第 1 主成分の符号が変わる", () => {
    const svd = componentsOf(new PCA(x));
    const covariance = componentsOf(new PCA(x, { method: "covarianceMatrix" }));

    expect(covariance[0]).toEqual(
      svd[0]?.map((value) => expect.closeTo(-value, 9)),
    );
  });
});
