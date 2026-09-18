import { readFileSync } from "node:fs";
import { parse } from "csv-parse/sync";
import {
  type TrainTestSplit,
  columnMeans,
  fillMissing,
  splitTrainTest,
} from "../chapter02/iris-preprocessing.ts";
import { multiply, solve, transpose } from "./matrix.ts";

export const COLUMNS = [
  "cinema_id",
  "SNS1",
  "SNS2",
  "actor",
  "original",
  "sales",
] as const;
export type CinemaRow = Record<(typeof COLUMNS)[number], number | null>;
export const FEATURES = ["SNS1", "SNS2", "actor", "original"] as const;
export type Feature = (typeof FEATURES)[number];
export const TARGET = "sales";

export function loadCinema(csvFile: string): CinemaRow[] {
  return parse<CinemaRow>(readFileSync(csvFile), {
    bom: true,
    columns: true,
    cast: (value, context) => {
      if (context.header) {
        return value;
      }
      return value === "" ? null : Number(value);
    },
  });
}

/** SNS2 がこの値を超え、かつ興行収入が OUTLIER_SALES 未満の映画を外れ値とする */
const OUTLIER_SNS2 = 1000;
const OUTLIER_SALES = 8500;

type OutlierColumns = Pick<CinemaRow, "SNS2" | "sales">;

function isOutlier({ SNS2, sales }: OutlierColumns): boolean {
  return (
    SNS2 !== null &&
    SNS2 > OUTLIER_SNS2 &&
    sales !== null &&
    sales < OUTLIER_SALES
  );
}

export function removeOutliers<R extends OutlierColumns>(
  rows: readonly R[],
): R[] {
  return rows.filter((row) => !isOutlier(row));
}

function targetOf(row: CinemaRow): number {
  const value = row[TARGET];
  if (value === null) {
    throw new Error(
      `${TARGET} が空欄の行があります（cinema_id: ${row.cinema_id}）`,
    );
  }
  return value;
}

export function prepareCinema(
  csvFile: string,
  testSize: number,
  seed: number,
): TrainTestSplit<Record<Feature, number>, number> {
  const rows = removeOutliers(loadCinema(csvFile));
  const x = rows.map(
    ({ cinema_id: _id, [TARGET]: _t, ...features }) => features,
  );
  const t = rows.map(targetOf);
  const split = splitTrainTest(x, t, testSize, seed);
  const means = columnMeans(split.xTrain, FEATURES);
  return {
    ...split,
    xTrain: fillMissing(split.xTrain, means),
    xTest: fillMissing(split.xTest, means),
  };
}

export interface LinearModel<K extends string> {
  intercept: number;
  coefficients: Record<K, number>;
}

export function fitLinearRegression<K extends string>(
  x: readonly Record<K, number>[],
  t: readonly number[],
): LinearModel<K> {
  const features = Object.keys(x[0] ?? {}) as K[];
  const design = x.map((row) => [1, ...features.map((name) => row[name])]);
  const target = t.map((value) => [value]);
  const designT = transpose(design);
  const w = solve(multiply(designT, design), multiply(designT, target));
  const [intercept = Number.NaN, ...coefficients] = transpose(w)[0] ?? [];
  return {
    intercept,
    coefficients: Object.fromEntries(
      features.map((name, i) => [name, coefficients[i] ?? Number.NaN]),
    ) as Record<K, number>,
  };
}

export function predict<K extends string>(
  model: LinearModel<K>,
  x: readonly Record<K, number>[],
): number[] {
  const features = Object.keys(model.coefficients) as K[];
  const matrix = x.map((row) => features.map((name) => row[name]));
  const weights = features.map((name) => [model.coefficients[name]]);
  return multiply(matrix, weights).map(
    ([value = Number.NaN]) => model.intercept + value,
  );
}
