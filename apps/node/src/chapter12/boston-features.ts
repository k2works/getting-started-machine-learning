import { readFileSync } from "node:fs";
import { parse } from "csv-parse/sync";
import { splitTrainTest } from "../chapter02/iris-preprocessing.ts";
import type { ValidationSplit } from "./regularization.ts";

export const FEATURES = ["RM", "PTRATIO", "LSTAT"] as const;
export type Feature = (typeof FEATURES)[number];
export const TARGET = "PRICE";
export const OUTLIER_THRESHOLD = 3;

export type BostonRow = Record<Feature | typeof TARGET, number>;

export interface BostonDataset extends ValidationSplit {
  readonly xTest: readonly (readonly number[])[];
  readonly tTest: readonly number[];
  readonly featureNames: readonly string[];
}

/** Boston.csv のうち、この章で使う列だけを数値として読み込む */
export function loadBoston(csvFile: string): BostonRow[] {
  const records = parse<Record<string, string>>(readFileSync(csvFile), {
    bom: true,
    columns: true,
  });
  return records.map((record) => ({
    RM: Number(record.RM),
    PTRATIO: Number(record.PTRATIO),
    LSTAT: Number(record.LSTAT),
    PRICE: Number(record.PRICE),
  }));
}

export function prepareBoston(
  csvFile: string,
  testSize: number,
  validationSize: number,
  seed: number,
): BostonDataset {
  const rows = removeOutliers(
    loadBoston(csvFile),
    [...FEATURES, TARGET],
    OUTLIER_THRESHOLD,
  );
  const x = rows.map(({ [TARGET]: _target, ...features }) => features);
  const t = rows.map((row) => row[TARGET]);
  const outer = splitTrainTest(x, t, testSize, seed);
  const inner = splitTrainTest(
    outer.xTrain,
    outer.tTrain,
    validationSize,
    seed,
  );
  const scaler = fitPolynomialScaler(inner.xTrain, FEATURES);
  return {
    xTrain: scaler.transform(inner.xTrain),
    tTrain: inner.tTrain,
    xValid: scaler.transform(inner.xTest),
    tValid: inner.tTest,
    xTest: scaler.transform(outer.xTest),
    tTest: outer.tTest,
    featureNames: scaler.featureNames,
  };
}

export interface PolynomialScaler<K extends string> {
  readonly featureNames: readonly string[];
  transform(rows: readonly Record<K, number>[]): number[][];
}

function mean(values: readonly number[]): number {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

/** 列ごとの平均値と標準偏差。標準偏差は偏差の二乗和を件数 n - ddof で割って求める */
function columnStats<K extends string>(
  rows: readonly Record<K, number>[],
  columns: readonly K[],
  ddof: 0 | 1,
): { column: K; mean: number; std: number }[] {
  return columns.map((column) => {
    const values = rows.map((row) => row[column]);
    const m = mean(values);
    const squares = values.reduce((sum, value) => sum + (value - m) ** 2, 0);
    return {
      column,
      mean: m,
      std: Math.sqrt(squares / (values.length - ddof)),
    };
  });
}

/**
 * 列ごとの z スコア（標準偏差は件数 n - 1 で割る標本標準偏差）の絶対値が
 * threshold を超える値を 1 つでも持つ行を除く
 */
export function removeOutliers<R extends Record<K, number>, K extends string>(
  rows: readonly R[],
  columns: readonly K[],
  threshold: number,
): R[] {
  const stats = columnStats(rows, columns, 1);
  const isOutlier = (row: R) =>
    stats.some((s) => Math.abs((row[s.column] - s.mean) / s.std) > threshold);
  return rows.filter((row) => !isOutlier(row));
}

/** 平均値と、件数 n で割る標準偏差（母標準偏差）を訓練データから求める */
export function fitPolynomialScaler<K extends string>(
  rows: readonly Record<K, number>[],
  columns: readonly K[],
): PolynomialScaler<K> {
  const stats = columnStats(rows, columns, 0);
  // 2 次の項を作る列の組（i <= j）。[0, 0] は 1 列目の 2 乗、[0, 1] は 1 列目と 2 列目の積
  const pairs = columns.flatMap((_, i) =>
    columns.slice(i).map((_, k) => [i, i + k] as const),
  );
  return {
    featureNames: [
      ...columns,
      ...pairs.map(([i, j]) =>
        i === j ? `${columns[i]}^2` : `${columns[i]} ${columns[j]}`,
      ),
    ],
    transform: (target) =>
      target.map((row) => {
        const z = stats.map((s) => (row[s.column] - s.mean) / s.std);
        return [...z, ...pairs.map(([i, j]) => (z[i] ?? NaN) * (z[j] ?? NaN))];
      }),
  };
}
