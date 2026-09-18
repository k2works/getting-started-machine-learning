import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { countMissing } from "../../src/chapter02/iris-preprocessing.ts";
import {
  FEATURES,
  type LinearModel,
  fitLinearRegression,
  loadCinema,
  predict,
  prepareCinema,
  removeOutliers,
} from "../../src/chapter07/cinema-regression.ts";

const HEADER = "cinema_id,SNS1,SNS2,actor,original,sales\n";

function writeCsv(rows: string): string {
  const csvFile = join(mkdtempSync(join(tmpdir(), "cinema-")), "cinema.csv");
  writeFileSync(csvFile, HEADER + rows);
  return csvFile;
}

describe("loadCinema", () => {
  it("CSV を読み込み数値の列を数値にし空欄を欠損値にする", () => {
    const rows = loadCinema(writeCsv("1,,500,9000.5,1,9500\n"));

    expect(rows).toEqual([
      {
        cinema_id: 1,
        SNS1: null,
        SNS2: 500,
        actor: 9000.5,
        original: 1,
        sales: 9500,
      },
    ]);
  });
});

describe("removeOutliers", () => {
  it("SNS2 が 1000 を超え売上が 8500 未満の行を取り除く", () => {
    const rows = [
      { SNS2: 1200, sales: 8000 },
      { SNS2: 600, sales: 9500 },
    ];

    expect(removeOutliers(rows)).toEqual([{ SNS2: 600, sales: 9500 }]);
  });

  it("条件の片方だけを満たす行は残す", () => {
    const rows = [
      { SNS2: 1200, sales: 9800 },
      { SNS2: 600, sales: 8000 },
    ];

    expect(removeOutliers(rows)).toEqual(rows);
  });
});

describe("prepareCinema", () => {
  it("外れ値を除き特徴量を選んで分割し欠損値を補完する", () => {
    const csvFile = writeCsv(
      "1,100,300,9000.0,0,9200\n" +
        "2,,400,9500.0,1,9800\n" +
        "3,300,500,,1,10100\n" +
        "4,150,1200,8800.0,0,8100\n" +
        "5,250,700,9900.0,1,10300\n" +
        "6,120,650,9100.0,0,9400\n",
    );

    const split = prepareCinema(csvFile, 0.4, 0);

    expect(Object.keys(split.xTrain[0] ?? {})).toEqual([...FEATURES]);
    expect([split.xTrain.length, split.xTest.length]).toEqual([3, 2]);
    expect([...split.tTrain, ...split.tTest]).not.toContain(8100);
    const rows = [...split.xTrain, ...split.xTest];
    expect(countMissing(rows, FEATURES)).toEqual({
      SNS1: 0,
      SNS2: 0,
      actor: 0,
      original: 0,
    });
  });

  it("興行収入が空欄の行があればエラーにする", () => {
    const csvFile = writeCsv("1,100,300,9000.0,0,\n");

    expect(() => prepareCinema(csvFile, 0.4, 0)).toThrow(
      "sales が空欄の行があります（cinema_id: 1）",
    );
  });
});

function expectModel<K extends string>(
  actual: LinearModel<K>,
  expected: LinearModel<K>,
): void {
  expect(actual).toEqual({
    intercept: expect.closeTo(expected.intercept, 9),
    coefficients: Object.fromEntries(
      Object.entries<number>(expected.coefficients).map(([name, value]) => [
        name,
        expect.closeTo(value, 9),
      ]),
    ),
  });
}

describe("fitLinearRegression", () => {
  it("直線上の点から切片と係数を求める", () => {
    const x = [{ x: 0 }, { x: 1 }, { x: 2 }, { x: 3 }];
    const t = [1, 3, 5, 7];

    const model = fitLinearRegression(x, t);

    expectModel(model, { intercept: 1, coefficients: { x: 2 } });
  });

  it("複数の特徴量から切片と係数を求める", () => {
    const x = [
      { a: 0, b: 0 },
      { a: 1, b: 0 },
      { a: 0, b: 1 },
      { a: 2, b: 1 },
      { a: 1, b: 3 },
    ];
    const t = x.map(({ a, b }) => 3 * a - 2 * b + 5);

    const model = fitLinearRegression(x, t);

    expectModel(model, { intercept: 5, coefficients: { a: 3, b: -2 } });
  });
});

describe("predict", () => {
  const model: LinearModel<"a" | "b"> = {
    intercept: 1,
    coefficients: { a: 2, b: -1 },
  };

  it("切片と係数から予測値を計算する", () => {
    const x = [
      { a: 1, b: 4 },
      { a: 3, b: 0.5 },
    ];

    expect(predict(model, x)).toEqual([-1, 6.5]);
  });

  it("係数の無いプロパティは予測に使わない", () => {
    const x = [
      { cinema_id: 1, a: 1, b: 4 },
      { cinema_id: 2, a: 3, b: 0.5 },
    ];

    expect(predict(model, x)).toEqual([-1, 6.5]);
  });
});
