import { existsSync } from "node:fs";
import { join } from "node:path";
import { Matrix } from "ml-matrix";
import { PCA } from "ml-pca";
import { beforeAll, describe, expect, it } from "vitest";
import {
  type NumericTable,
  loadStandardizedBoston,
} from "../../src/chapter13/boston-standardized.ts";
import { main } from "../../src/chapter13/main.ts";
import {
  covarianceMatrix,
  fitPca,
  normalizeSigns,
} from "../../src/chapter13/pca.ts";
import { dataDir } from "../../src/dataset.ts";

const csvFile = join(dataDir(), "Boston.csv");

describe.skipIf(!existsSync(csvFile))("Boston.csv の実データ", () => {
  let table: NumericTable;

  beforeAll(() => {
    table = loadStandardizedBoston(csvFile);
  });

  it("CRIME をダミー変数にして 15 列の標準化済みデータにする", () => {
    expect([table.x.length, table.columns.length]).toEqual([100, 15]);
  });

  it("実データの主成分も分散共分散行列の固有ベクトルになる", () => {
    const model = fitPca(table.x, 15);

    const covariance = new Matrix(covarianceMatrix(table.x));
    model.components.forEach((component, i) => {
      const variance = model.explainedVariance[i] as number;
      const projected = covariance.mmul(Matrix.columnVector(component));
      expect(projected.to1DArray()).toEqual(
        component.map((value) => expect.closeTo(value * variance, 9)),
      );
    });
    expect(model.explainedVarianceRatio.reduce((a, b) => a + b)).toBeCloseTo(
      1,
      9,
    );
  });

  it("ml-pca と寄与率・主成分が一致する", () => {
    const mine = fitPca(table.x, 15);
    const library = new PCA(table.x);

    expect(library.getExplainedVariance()).toEqual(
      mine.explainedVarianceRatio.map((r) => expect.closeTo(r, 9)),
    );
    const components = library.getEigenvectors().transpose().to2DArray();
    expect(normalizeSigns(components)).toEqual(
      mine.components.map((row) => row.map((v) => expect.closeTo(v, 9))),
    );
  });

  it("実行すると寄与率と主成分の解釈を表示する", () => {
    const lines: string[] = [];

    main((line) => lines.push(line));

    expect(lines).toEqual([
      "データ件数: 100, 列数: 15",
      "寄与率: PC1 0.4110, PC2 0.1448, PC3 0.1019, PC4 0.0645, PC5 0.0623, PC6 0.0581",
      "累積寄与率が 0.8 に届く主成分の数: 6（累積寄与率 0.8427）",
      "第 1 主成分で影響の大きい列: INDUS 0.359, NOX 0.350, TAX 0.328",
      "第 2 主成分で影響の大きい列: PRICE 0.444, low 0.423, RM 0.405",
    ]);
  });
});
