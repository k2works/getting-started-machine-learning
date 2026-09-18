import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  fitPolynomialScaler,
  prepareBoston,
  removeOutliers,
} from "../../src/chapter12/boston-features.ts";

function closeTo(values: readonly number[]) {
  return values.map((value) => expect.closeTo(value, 12));
}

describe("fitPolynomialScaler", () => {
  it("訓練データで標準化してから 2 乗の列を加える", () => {
    const x = [{ RM: 1 }, { RM: 2 }, { RM: 3 }];

    const scaler = fitPolynomialScaler(x, ["RM"]);

    const z = 1.224744871391589;
    expect(scaler.transform(x)).toEqual([
      closeTo([-z, z * z]),
      closeTo([0, 0]),
      closeTo([z, z * z]),
    ]);
  });

  it("テストデータも訓練データの平均値と標準偏差で標準化する", () => {
    const train = [{ RM: 1 }, { RM: 2 }, { RM: 3 }];
    const test = [{ RM: 2 }];

    const scaler = fitPolynomialScaler(train, ["RM"]);

    expect(scaler.transform(test)).toEqual([closeTo([0, 0])]);
  });

  it("2 つの特徴量から 2 乗と交互作用の列を作り名前を付ける", () => {
    const x = [
      { RM: 1, LSTAT: 3 },
      { RM: 2, LSTAT: 1 },
      { RM: 3, LSTAT: 2 },
    ];

    const scaler = fitPolynomialScaler(x, ["RM", "LSTAT"]);

    expect(scaler.featureNames).toEqual([
      "RM",
      "LSTAT",
      "RM^2",
      "RM LSTAT",
      "LSTAT^2",
    ]);
    expect(scaler.transform(x).map((row) => row.length)).toEqual([5, 5, 5]);
  });
});

describe("removeOutliers", () => {
  it("平均から標準偏差の 3 倍より離れた値を持つ行を除く", () => {
    const rows = [...Array<number>(11).fill(1), 100].map((RM) => ({ RM }));

    expect(removeOutliers(rows, ["RM"], 3)).toEqual(Array(11).fill({ RM: 1 }));
  });

  it("外れ値が無ければすべての行を残す", () => {
    const rows = [{ RM: 5 }, { RM: 6 }, { RM: 7 }];

    expect(removeOutliers(rows, ["RM"], 3)).toHaveLength(3);
  });

  it("指定した列の値だけで外れ値を判定する", () => {
    const rows = [...Array<number>(11).fill(0), 100].map((ZN) => ({
      RM: 1,
      ZN,
    }));

    expect(removeOutliers(rows, ["RM"], 3)).toHaveLength(12);
  });
});

describe("prepareBoston", () => {
  it("訓練データと検証データとテストデータに分けて多項式特徴量を作る", () => {
    const lines = Array.from(
      { length: 10 },
      (_, i) => `low,6.${i},1${i}.5,${i + 3}.2,2${i}.0`,
    );
    const csvFile = join(mkdtempSync(join(tmpdir(), "chapter12-")), "b.csv");
    writeFileSync(
      csvFile,
      `CRIME,RM,PTRATIO,LSTAT,PRICE\n${lines.join("\n")}\n`,
    );

    const dataset = prepareBoston(csvFile, 0.3, 0.3, 0);

    const shapes = [dataset.xTrain, dataset.xValid, dataset.xTest].map((x) => [
      x.length,
      x[0]?.length,
    ]);
    expect(shapes).toEqual([
      [4, 9],
      [3, 9],
      [3, 9],
    ]);
    expect(
      [dataset.tTrain, dataset.tValid, dataset.tTest].map((t) => t.length),
    ).toEqual([4, 3, 3]);
    expect(dataset.featureNames.slice(0, 3)).toEqual([
      "RM",
      "PTRATIO",
      "LSTAT",
    ]);
  });
});
