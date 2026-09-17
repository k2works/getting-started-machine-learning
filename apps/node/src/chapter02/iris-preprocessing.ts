import { readFileSync } from "node:fs";
import { parse } from "csv-parse/sync";
import { createRandom, shuffle } from "./random.ts";

export const FEATURES = [
  "がく片長さ",
  "がく片幅",
  "花弁長さ",
  "花弁幅",
] as const;
export type Feature = (typeof FEATURES)[number];
export const TARGET = "種類";

export type IrisRow = Record<Feature, number | null> & { [TARGET]: string };

export function loadIris(csvFile: string): IrisRow[] {
  return parse<IrisRow>(readFileSync(csvFile), {
    bom: true,
    columns: true,
    cast: (value, context) => {
      if (context.header || context.column === TARGET) {
        return value;
      }
      return value === "" ? null : Number(value);
    },
  });
}

function byColumn<K extends string>(
  columns: readonly K[],
  valueOf: (column: K) => number,
): Record<K, number> {
  return Object.fromEntries(
    columns.map((column) => [column, valueOf(column)]),
  ) as Record<K, number>;
}

export function countMissing<K extends string>(
  rows: readonly Record<K, unknown>[],
  columns: readonly K[],
): Record<K, number> {
  return byColumn(
    columns,
    (column) => rows.filter((row) => row[column] === null).length,
  );
}

export function columnMeans<K extends string>(
  rows: readonly Record<K, number | null>[],
  columns: readonly K[],
): Record<K, number> {
  return byColumn(columns, (column) => {
    const values = rows
      .map((row) => row[column])
      .filter((value) => value !== null);
    return values.reduce((sum, value) => sum + value, 0) / values.length;
  });
}

export function fillMissing<K extends string>(
  rows: readonly Record<K, number | null>[],
  values: Record<K, number>,
): Record<K, number>[] {
  return rows.map(
    (row) =>
      Object.fromEntries(
        Object.entries(row).map(([column, value]) => [
          column,
          value ?? values[column as K],
        ]),
      ) as Record<K, number>,
  );
}

export function splitFeaturesAndTarget(rows: readonly IrisRow[]): {
  x: Record<Feature, number | null>[];
  t: string[];
} {
  return {
    x: rows.map(({ [TARGET]: _target, ...features }) => features),
    t: rows.map((row) => row[TARGET]),
  };
}

export interface TrainTestSplit<X, T> {
  xTrain: X[];
  xTest: X[];
  tTrain: T[];
  tTest: T[];
}

function pick<T>(items: readonly T[], positions: readonly number[]): T[] {
  return positions.map((i) => items[i] as T);
}

export function splitTrainTest<X, T>(
  x: readonly X[],
  t: readonly T[],
  testSize: number,
  seed: number,
): TrainTestSplit<X, T> {
  const positions = shuffle(
    x.map((_, i) => i),
    createRandom(seed),
  );
  const nTrain = x.length - Math.ceil(x.length * testSize);
  const train = positions.slice(0, nTrain);
  const test = positions.slice(nTrain);
  return {
    xTrain: pick(x, train),
    xTest: pick(x, test),
    tTrain: pick(t, train),
    tTest: pick(t, test),
  };
}

export function prepareIris(
  csvFile: string,
  testSize: number,
  seed: number,
): TrainTestSplit<Record<Feature, number>, string> {
  const { x, t } = splitFeaturesAndTarget(loadIris(csvFile));
  const split = splitTrainTest(x, t, testSize, seed);
  const means = columnMeans(split.xTrain, FEATURES);
  return {
    ...split,
    xTrain: fillMissing(split.xTrain, means),
    xTest: fillMissing(split.xTest, means),
  };
}
