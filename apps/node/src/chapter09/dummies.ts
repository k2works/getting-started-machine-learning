export function dummyCategories(values: readonly (string | null)[]): string[] {
  return [...new Set(values.filter((value) => value !== null))].sort().slice(1);
}

export function encodeDummies<R extends Record<C, unknown>, C extends string>(
  rows: readonly R[],
  column: C,
  categories: readonly string[],
): (Omit<R, C> & Record<string, number>)[] {
  return rows.map((row) => {
    const { [column]: value, ...rest } = row;
    const flags = Object.fromEntries(
      categories.map((category) => [
        `${column}_${category}`,
        value === category ? 1 : 0,
      ]),
    );
    return { ...rest, ...flags };
  });
}
