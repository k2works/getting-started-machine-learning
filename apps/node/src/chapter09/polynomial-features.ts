export function polynomialFeatures<K extends string>(
  rows: readonly Record<K, number>[],
  columns: readonly K[],
): Record<string, number>[] {
  return rows.map((row) => {
    const features: Record<string, number> = {};
    for (const column of columns) {
      features[column] = row[column];
    }
    for (const [left, right] of pairsWithReplacement(columns)) {
      features[termName(left, right)] = row[left] * row[right];
    }
    return features;
  });
}

export function pairsWithReplacement<T>(items: readonly T[]): [T, T][] {
  return items.flatMap((left, i) =>
    items.slice(i).map((right): [T, T] => [left, right]),
  );
}

export function termName(left: string, right: string): string {
  return left === right ? `${left}^2` : `${left} ${right}`;
}
