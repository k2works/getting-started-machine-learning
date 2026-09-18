import { Matrix } from "ml-matrix";
import { describe, expect, it } from "vitest";
import { Standardizer } from "../../src/chapter09/standardizer.ts";

// ml-matrix の center と scale で、1 列の値を列ごとに標準化する
function mlMatrixStandardize(values: readonly number[]): number[] {
  return new Matrix(values.map((value) => [value]))
    .center("column")
    .scale("column")
    .to1DArray();
}

function closeTo(values: readonly number[]): unknown[] {
  return values.map((value) => expect.closeTo(value, 12));
}

describe("ml-matrix の標準化", () => {
  const train = [5.5, 6.0, 7.5, 6.5];
  const rows = train.map((value) => ({ RM: value }));

  it("ml-matrix の scale は件数から 1 を引いて割る標準偏差を使う", () => {
    const matrix = new Matrix(rows.map((row) => [row.RM]));
    const mean = matrix.mean("column")[0] as number;
    const sampleStd = matrix.standardDeviation("column")[0] as number;

    expect(mlMatrixStandardize(train)).toEqual(
      closeTo(train.map((value) => (value - mean) / sampleStd)),
    );
  });

  it("自作の標準化に件数から決まる係数を掛けると ml-matrix の値になる", () => {
    const standardized = Standardizer.fit(rows, ["RM"]).transform(rows);
    const ratio = Math.sqrt((train.length - 1) / train.length);

    expect(standardized.map((row) => row.RM * ratio)).toEqual(
      closeTo(mlMatrixStandardize(train)),
    );
  });
});
