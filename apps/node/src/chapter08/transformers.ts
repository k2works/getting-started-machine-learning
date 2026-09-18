/** 列 K の null を取り除いた行の型 */
export type Filled<R, K extends keyof R> = Omit<R, K> & {
  [P in K]: NonNullable<R[P]>;
};

/** グループごとの中央値で補完するために、訓練データから求めた値 */
export interface GroupMedianImputer<K extends string, B extends string> {
  column: K;
  by: readonly B[];
  /** グループのキー（JSON の文字列）ごとの中央値 */
  medians: Record<string, number>;
  overallMedian: number;
}

function median(values: readonly number[]): number {
  const sorted = values.toSorted((a, b) => a - b);
  const middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 === 1
    ? (sorted[middle] as number)
    : ((sorted[middle - 1] as number) + (sorted[middle] as number)) / 2;
}

function groupKey<B extends string>(
  row: Record<B, unknown>,
  by: readonly B[],
): string {
  return JSON.stringify(by.map((name) => row[name]));
}

export function fitGroupMedianImputer<K extends string, B extends string>(
  x: readonly (Record<K, number | null> & Record<B, unknown>)[],
  column: K,
  by: readonly B[],
): GroupMedianImputer<K, B> {
  const groups = new Map<string, number[]>();
  for (const row of x) {
    const value = row[column];
    if (value === null) {
      continue;
    }
    const key = groupKey(row, by);
    groups.set(key, [...(groups.get(key) ?? []), value]);
  }
  const medians = Object.fromEntries(
    [...groups].map(([key, values]) => [key, median(values)]),
  );
  const overallMedian = median([...groups.values()].flat());
  return { column, by, medians, overallMedian };
}

export function imputeGroupMedian<
  K extends string,
  B extends string,
  R extends Record<K, number | null> & Record<B, unknown>,
>(x: readonly R[], imputer: GroupMedianImputer<K, B>): Filled<R, K>[] {
  const { column, by, medians, overallMedian } = imputer;
  return x.map(
    (row) =>
      ({
        ...row,
        [column]: row[column] ?? medians[groupKey(row, by)] ?? overallMedian,
      }) as Filled<R, K>,
  );
}

/** 最も多い値で補完するために、訓練データから求めた値 */
export interface MostFrequentImputer<K extends string> {
  column: K;
  mostFrequent: string;
}

export function fitMostFrequentImputer<K extends string>(
  x: readonly Record<K, string | null>[],
  column: K,
): MostFrequentImputer<K> {
  const counts = new Map<string, number>();
  for (const row of x) {
    const value = row[column];
    if (value !== null) {
      counts.set(value, (counts.get(value) ?? 0) + 1);
    }
  }
  let mostFrequent = "";
  let mostCount = 0;
  for (const [value, count] of counts) {
    if (count > mostCount) {
      mostFrequent = value;
      mostCount = count;
    }
  }
  return { column, mostFrequent };
}

export function imputeMostFrequent<
  K extends string,
  R extends Record<K, string | null>,
>(x: readonly R[], imputer: MostFrequentImputer<K>): Filled<R, K>[] {
  const { column, mostFrequent } = imputer;
  return x.map(
    (row) =>
      ({ ...row, [column]: row[column] ?? mostFrequent }) as Filled<R, K>,
  );
}

/** カテゴリの列 C を取り除き、ダミー変数の列（列名_カテゴリ）を加えた行の型 */
export type Encoded<R, C extends keyof R> = Omit<R, C> & Record<string, number>;

export interface DummyEncoder<C extends string> {
  /** 列ごとの、ダミー変数にするカテゴリ（最初のカテゴリを除く） */
  dummies: Record<C, string[]>;
}

function categoriesOf<C extends string>(
  x: readonly Record<C, string>[],
  column: C,
): string[] {
  return [...new Set(x.map((row) => row[column]))].toSorted();
}

export function fitDummyEncoder<C extends string>(
  x: readonly Record<C, string>[],
  columns: readonly C[],
): DummyEncoder<C> {
  const dummies = Object.fromEntries(
    columns.map((column) => [column, categoriesOf(x, column).slice(1)]),
  ) as Record<C, string[]>;
  return { dummies };
}

export function encodeDummies<C extends string, R extends Record<C, string>>(
  x: readonly R[],
  encoder: DummyEncoder<C>,
): Encoded<R, C>[] {
  const columns = Object.keys(encoder.dummies) as C[];
  return x.map((row) => {
    const encoded: Record<string, unknown> = { ...row };
    for (const column of columns) {
      const categories = encoder.dummies[column];
      for (const category of categories) {
        encoded[`${column}_${category}`] = row[column] === category ? 1 : 0;
      }
      delete encoded[column];
    }
    return encoded as Encoded<R, C>;
  });
}
