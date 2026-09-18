import { columnMeans } from "../chapter02/iris-preprocessing.ts";
import { mean } from "./statistics.ts";

export class Standardizer<K extends string> {
  readonly means: Record<K, number>;
  readonly stds: Record<K, number>;

  constructor(means: Record<K, number>, stds: Record<K, number>) {
    this.means = means;
    this.stds = stds;
  }

  transform<R extends Record<K, number>>(rows: readonly R[]): R[] {
    const columns = Object.keys(this.means) as K[];
    return rows.map((row) => {
      const standardized = { ...row };
      for (const column of columns) {
        standardized[column] = ((row[column] - this.means[column]) /
          this.stds[column]) as R[K];
      }
      return standardized;
    });
  }

  static fit<K extends string>(
    rows: readonly Record<K, number>[],
    columns: readonly K[],
  ): Standardizer<K> {
    const means = columnMeans(rows, columns);
    const stds = Object.fromEntries(
      columns.map((column) => {
        const std = Math.sqrt(
          mean(rows.map((row) => (row[column] - means[column]) ** 2)),
        );
        return [column, std === 0 ? 1 : std];
      }),
    ) as Record<K, number>;
    return new Standardizer(means, stds);
  }
}
