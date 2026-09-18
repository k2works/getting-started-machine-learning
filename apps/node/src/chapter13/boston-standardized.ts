import { readFileSync } from "node:fs";
import { parse } from "csv-parse/sync";
import { columnMeans } from "../chapter02/iris-preprocessing.ts";

const CRIME = "CRIME";

export const NUMERIC_COLUMNS = [
  "ZN",
  "INDUS",
  "CHAS",
  "NOX",
  "RM",
  "AGE",
  "DIS",
  "RAD",
  "TAX",
  "PTRATIO",
  "B",
  "LSTAT",
  "PRICE",
] as const;
export type NumericColumn = (typeof NUMERIC_COLUMNS)[number];

/** 数値の列と、カテゴリの列 CRIME を持つ 1 行 */
export type BostonLikeRow<K extends string> = Record<K, number | null> & {
  [CRIME]: string;
};

/** 列名と、1 行を数値の配列で表したデータ */
export interface NumericTable {
  columns: string[];
  x: number[][];
}

/** 平均 0・標準偏差 1 にそろえる（標準偏差は件数 n で割る） */
function standardize(values: number[]): number[] {
  const mean = values.reduce((sum, v) => sum + v, 0) / values.length;
  const variance =
    values.reduce((sum, v) => sum + (v - mean) ** 2, 0) / values.length;
  return values.map((v) => (v - mean) / Math.sqrt(variance));
}

export function standardizeBoston<K extends string>(
  rows: readonly BostonLikeRow<K>[],
  columns: readonly K[],
): NumericTable {
  const crime = rows.map((row) => row[CRIME]);
  const categories = [...new Set(crime)].sort().slice(1);
  const means = columnMeans(rows, columns);
  const numeric = columns.map((column) =>
    rows.map((row) => row[column] ?? means[column]),
  );
  const dummies = categories.map((category) =>
    crime.map((value) => (value === category ? 1 : 0)),
  );
  const standardized = [...numeric, ...dummies].map(standardize);
  return {
    columns: [...columns, ...categories],
    x: rows.map((_, i) => standardized.map((values) => values[i] as number)),
  };
}

export function loadBoston(csvFile: string): BostonLikeRow<NumericColumn>[] {
  return parse<BostonLikeRow<NumericColumn>>(readFileSync(csvFile), {
    bom: true,
    columns: true,
    cast: (value, context) => {
      if (context.header || context.column === CRIME) {
        return value;
      }
      return value === "" ? null : Number(value);
    },
  });
}

export function loadStandardizedBoston(csvFile: string): NumericTable {
  return standardizeBoston(loadBoston(csvFile), NUMERIC_COLUMNS);
}
