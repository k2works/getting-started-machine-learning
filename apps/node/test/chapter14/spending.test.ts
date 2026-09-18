import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  loadSpending,
  standardize,
  summarizeClusters,
} from "../../src/chapter14/spending.ts";

const HEADER =
  "Channel,Region,Fresh,Milk,Grocery,Frozen,Detergents_Paper,Delicassen\n";

function writeCsv(rows: string): string {
  const csvFile = join(mkdtempSync(join(tmpdir(), "wholesale-")), "w.csv");
  writeFileSync(csvFile, HEADER + rows);
  return csvFile;
}

describe("loadSpending", () => {
  it("Channel と Region を除いた支出額の列を数値として読み込む", () => {
    const rows = loadSpending(writeCsv("1,2,100,200,300,400,500,600\n"));

    expect(rows).toEqual([
      {
        Fresh: 100,
        Milk: 200,
        Grocery: 300,
        Frozen: 400,
        Detergents_Paper: 500,
        Delicassen: 600,
      },
    ]);
  });
});

describe("standardize", () => {
  it("列ごとに平均 0・標準偏差 1 の点の配列に変換する", () => {
    const rows = [
      { Fresh: 10, Milk: 5 },
      { Fresh: 20, Milk: 5 },
      { Fresh: 30, Milk: 8 },
    ];

    const points = standardize(rows, ["Fresh", "Milk"]);

    for (const column of [0, 1]) {
      const values = points.map((point) => point[column] as number);
      const mean = values.reduce((sum, v) => sum + v, 0) / values.length;
      const variance =
        values.reduce((sum, v) => sum + (v - mean) ** 2, 0) / values.length;
      expect(mean).toBeCloseTo(0, 12);
      expect(Math.sqrt(variance)).toBeCloseTo(1, 12);
    }
  });
});

describe("summarizeClusters", () => {
  it("クラスタごとの件数と平均を件数の多い順に並べる", () => {
    const rows = [
      { Fresh: 100, Milk: 20 },
      { Fresh: 300, Milk: 40 },
      { Fresh: 1000, Milk: 900 },
    ];

    const summary = summarizeClusters(rows, ["Fresh", "Milk"], [1, 1, 0]);

    expect(summary).toEqual([
      { cluster: 1, count: 2, means: { Fresh: 200, Milk: 30 } },
      { cluster: 0, count: 1, means: { Fresh: 1000, Milk: 900 } },
    ]);
  });
});
