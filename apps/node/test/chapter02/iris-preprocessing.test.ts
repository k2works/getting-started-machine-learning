import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  columnMeans,
  countMissing,
  fillMissing,
  FEATURES,
  loadIris,
  prepareIris,
  splitFeaturesAndTarget,
  splitTrainTest,
} from "../../src/chapter02/iris-preprocessing.ts";

const HEADER = "\uFEFFがく片長さ,がく片幅,花弁長さ,花弁幅,種類\n";

function writeCsv(rows: string): string {
  const csvFile = join(mkdtempSync(join(tmpdir(), "iris-")), "iris.csv");
  writeFileSync(csvFile, HEADER + rows);
  return csvFile;
}

describe("loadIris", () => {
  it("BOM 付き CSV の列名を BOM なしのプロパティ名にし、数値の列を数値にする", () => {
    const rows = loadIris(writeCsv("0.1,0.2,0.3,0.4,Iris-setosa\n"));

    expect(rows).toEqual([
      {
        がく片長さ: 0.1,
        がく片幅: 0.2,
        花弁長さ: 0.3,
        花弁幅: 0.4,
        種類: "Iris-setosa",
      },
    ]);
  });

  it("空欄は欠損値の null として読み込む", () => {
    const rows = loadIris(writeCsv("0.1,,0.3,0.4,Iris-setosa\n"));

    expect(rows[0]?.がく片幅).toBeNull();
  });
});

describe("countMissing", () => {
  it("列ごとの欠損値の数を数える", () => {
    const rows = [
      { がく片長さ: 0.1, がく片幅: 0.2, 種類: "Iris-setosa" },
      { がく片長さ: null, がく片幅: 0.3, 種類: "Iris-setosa" },
      { がく片長さ: null, がく片幅: null, 種類: "Iris-virginica" },
    ];

    expect(countMissing(rows, ["がく片長さ", "がく片幅", "種類"])).toEqual({
      がく片長さ: 2,
      がく片幅: 1,
      種類: 0,
    });
  });
});

describe("columnMeans", () => {
  it("欠損値を除いて列ごとの平均値を求める", () => {
    const rows = [
      { がく片長さ: 0.1, がく片幅: 0.2 },
      { がく片長さ: null, がく片幅: 0.4 },
      { がく片長さ: 0.3, がく片幅: 0.9 },
    ];

    const means = columnMeans(rows, ["がく片長さ", "がく片幅"]);

    expect(means.がく片長さ).toBeCloseTo(0.2, 12);
    expect(means.がく片幅).toBeCloseTo(0.5, 12);
  });
});

describe("fillMissing", () => {
  it("欠損値を列ごとに指定した値で補完する", () => {
    const rows = [
      { がく片長さ: 0.1, がく片幅: null },
      { がく片長さ: null, がく片幅: 0.4 },
    ];

    const filled = fillMissing(rows, { がく片長さ: 0.2, がく片幅: 0.5 });

    expect(filled).toEqual([
      { がく片長さ: 0.1, がく片幅: 0.5 },
      { がく片長さ: 0.2, がく片幅: 0.4 },
    ]);
  });

  it("元の配列は変更しない", () => {
    const rows = [{ がく片長さ: null }];

    fillMissing(rows, { がく片長さ: 0.2 });

    expect(rows).toEqual([{ がく片長さ: null }]);
  });
});

describe("splitFeaturesAndTarget", () => {
  it("特徴量の列と正解ラベルの列に分ける", () => {
    const rows = [
      {
        がく片長さ: 0.1,
        がく片幅: 0.2,
        花弁長さ: 0.3,
        花弁幅: 0.4,
        種類: "Iris-setosa",
      },
      {
        がく片長さ: 0.5,
        がく片幅: null,
        花弁長さ: 0.7,
        花弁幅: 0.8,
        種類: "Iris-virginica",
      },
    ];

    const { x, t } = splitFeaturesAndTarget(rows);

    expect(x).toEqual([
      { がく片長さ: 0.1, がく片幅: 0.2, 花弁長さ: 0.3, 花弁幅: 0.4 },
      { がく片長さ: 0.5, がく片幅: null, 花弁長さ: 0.7, 花弁幅: 0.8 },
    ]);
    expect(t).toEqual(["Iris-setosa", "Iris-virginica"]);
  });
});

function numberedDataset(size: number): { x: { x: number }[]; t: string[] } {
  const numbers = Array.from({ length: size }, (_, i) => i);
  return {
    x: numbers.map((i) => ({ x: i })),
    t: numbers.map((i) => `label${i}`),
  };
}

describe("splitTrainTest", () => {
  it("テストデータの割合どおりの件数に分ける", () => {
    const { x, t } = numberedDataset(10);

    const split = splitTrainTest(x, t, 0.3, 0);

    expect([split.xTrain.length, split.xTest.length]).toEqual([7, 3]);
    expect([split.tTrain.length, split.tTest.length]).toEqual([7, 3]);
  });

  it("件数が変わってもテストデータの割合どおりに分ける", () => {
    const { x, t } = numberedDataset(20);

    const split = splitTrainTest(x, t, 0.25, 0);

    expect([split.xTrain.length, split.xTest.length]).toEqual([15, 5]);
    expect([split.tTrain.length, split.tTest.length]).toEqual([15, 5]);
  });

  it("すべての行を重複なく訓練データとテストデータのどちらかに入れる", () => {
    const { x, t } = numberedDataset(10);

    const split = splitTrainTest(x, t, 0.3, 0);

    const numbers = [...split.xTrain, ...split.xTest].map((row) => row.x);
    expect(numbers.toSorted((a, b) => a - b)).toEqual([
      0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
    ]);
  });

  it("特徴量と正解ラベルの対応を保ったまま分ける", () => {
    const { x, t } = numberedDataset(10);

    const split = splitTrainTest(x, t, 0.3, 0);

    expect(split.xTrain.map((row) => `label${row.x}`)).toEqual(split.tTrain);
    expect(split.xTest.map((row) => `label${row.x}`)).toEqual(split.tTest);
  });

  it("同じシードなら同じ分け方になる", () => {
    const { x, t } = numberedDataset(10);

    const first = splitTrainTest(x, t, 0.3, 42);
    const second = splitTrainTest(x, t, 0.3, 42);

    expect(first.tTest).toEqual(second.tTest);
  });

  it("シードが違えば違う分け方になる", () => {
    const { x, t } = numberedDataset(10);

    const first = splitTrainTest(x, t, 0.3, 0);
    const second = splitTrainTest(x, t, 0.3, 1);

    expect(first.tTest).not.toEqual(second.tTest);
  });
});

describe("prepareIris", () => {
  it("訓練データとテストデータのどちらにも欠損値が残らない", () => {
    const csvFile = writeCsv(
      "0.1,,0.3,0.4,Iris-setosa\n" +
        "0.2,0.3,,0.5,Iris-setosa\n" +
        ",0.4,0.5,0.6,Iris-virginica\n" +
        "0.4,0.5,0.6,,Iris-virginica\n",
    );

    const split = prepareIris(csvFile, 0.5, 0);

    expect(Object.values(countMissing(split.xTrain, FEATURES))).toEqual([
      0, 0, 0, 0,
    ]);
    expect(Object.values(countMissing(split.xTest, FEATURES))).toEqual([
      0, 0, 0, 0,
    ]);
  });
});
