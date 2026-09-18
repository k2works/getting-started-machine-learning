import { describe, expect, it } from "vitest";
import { standardizeBoston } from "../../src/chapter13/boston-standardized.ts";

const COLUMNS = ["RM", "PRICE"] as const;

function bostonLike() {
  return [
    { CRIME: "high", RM: 5, PRICE: 10 },
    { CRIME: "low", RM: 6, PRICE: 20 },
    { CRIME: "very_low", RM: null, PRICE: 30 },
    { CRIME: "low", RM: 7, PRICE: 40 },
  ];
}

describe("standardizeBoston", () => {
  it("CRIME をダミー変数の列に置き換える", () => {
    const table = standardizeBoston(bostonLike(), COLUMNS);

    expect(table.columns).toEqual(["RM", "PRICE", "low", "very_low"]);
  });

  it("欠損値を補完してから各列を平均 0 と標準偏差 1 にそろえる", () => {
    const table = standardizeBoston(bostonLike(), COLUMNS);

    table.columns.forEach((column, j) => {
      const values = table.x.map((row) => row[j] as number);
      const mean = values.reduce((sum, v) => sum + v, 0) / values.length;
      const variance =
        values.reduce((sum, v) => sum + (v - mean) ** 2, 0) / values.length;
      expect({ column, mean, std: Math.sqrt(variance) }).toEqual({
        column,
        mean: expect.closeTo(0, 9),
        std: expect.closeTo(1, 9),
      });
    });
  });

  it("欠損値は同じ列の平均値で補完する", () => {
    const table = standardizeBoston(bostonLike(), COLUMNS);

    // RM は 5・6・7 の平均 6 で補完されて 5・6・6・7 になり、標準偏差は √0.5
    const rm = table.x.map((row) => row[0]);
    expect(rm).toEqual(
      [-Math.SQRT2, 0, 0, Math.SQRT2].map((v) => expect.closeTo(v, 9)),
    );
  });
});
