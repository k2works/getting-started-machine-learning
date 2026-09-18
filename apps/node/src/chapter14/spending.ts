import { readFileSync } from "node:fs";
import { parse } from "csv-parse/sync";
import { mean, type Point } from "./kmeans.ts";

export const SPENDING_COLUMNS = [
  "Fresh",
  "Milk",
  "Grocery",
  "Frozen",
  "Detergents_Paper",
  "Delicassen",
] as const;
export type SpendingColumn = (typeof SPENDING_COLUMNS)[number];
export type Spending = Record<SpendingColumn, number>;

type WholesaleRow = Spending & { Channel: number; Region: number };

export function loadSpending(csvFile: string): Spending[] {
  const rows = parse<WholesaleRow>(readFileSync(csvFile), {
    bom: true,
    columns: true,
    cast: (value, context) => (context.header ? value : Number(value)),
  });
  return rows.map(({ Channel, Region, ...spending }) => spending);
}

/** 列ごとに平均を引き、標準偏差（件数で割る ddof = 0）で割った点の配列にする。 */
export function standardize<K extends string>(
  rows: readonly Record<K, number>[],
  columns: readonly K[],
): Point[] {
  const stats = columns.map((column) => {
    const values = rows.map((row) => row[column]);
    const center = mean(values);
    const std = Math.sqrt(mean(values.map((value) => (value - center) ** 2)));
    return { center, std };
  });
  return rows.map((row) =>
    columns.map((column, j) => {
      const { center, std } = stats[j] as { center: number; std: number };
      return (row[column] - center) / std;
    }),
  );
}

export interface ClusterSummary<K extends string> {
  cluster: number;
  count: number;
  means: Record<K, number>;
}

/** クラスタごとの件数と列ごとの平均を、件数の多い順に並べる。 */
export function summarizeClusters<K extends string>(
  rows: readonly Record<K, number>[],
  columns: readonly K[],
  labels: readonly number[],
): ClusterSummary<K>[] {
  const groups = new Map<number, Record<K, number>[]>();
  rows.forEach((row, i) => {
    const cluster = labels[i] as number;
    const members = groups.get(cluster) ?? [];
    members.push(row);
    groups.set(cluster, members);
  });
  return [...groups]
    .map(([cluster, members]) => ({
      cluster,
      count: members.length,
      means: Object.fromEntries(
        columns.map((column) => [
          column,
          mean(members.map((row) => row[column])),
        ]),
      ) as Record<K, number>,
    }))
    .sort((a, b) => b.count - a.count);
}
