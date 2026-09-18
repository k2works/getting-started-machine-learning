export type Matrix = number[][];

function dot(u: readonly number[], v: readonly number[]): number {
  return u.reduce((sum, ui, i) => sum + ui * (v[i] ?? Number.NaN), 0);
}

export function multiply(a: Matrix, b: Matrix): Matrix {
  const columnCount = a[0]?.length ?? 0;
  if (columnCount !== b.length) {
    throw new Error(
      `左の行列の列数 ${columnCount} と右の行列の行数 ${b.length} が違います`,
    );
  }
  const columns = transpose(b);
  return a.map((row) => columns.map((column) => dot(row, column)));
}

export function transpose(a: Matrix): Matrix {
  return (a[0] ?? []).map((_, j) => a.map((row) => row[j] ?? Number.NaN));
}

/** i 行 j 列の値。範囲の外は NaN にし、計算に混ざれば結果が NaN になるようにする */
function get(m: Matrix, i: number, j: number): number {
  return m[i]?.[j] ?? Number.NaN;
}

export function solve(a: Matrix, b: Matrix): Matrix {
  const n = a.length;
  const augmented = a.map((row, i) => [...row, ...(b[i] ?? [])]);
  for (let pivot = 0; pivot < n; pivot++) {
    let largest = pivot;
    for (let i = pivot + 1; i < n; i++) {
      if (
        Math.abs(get(augmented, i, pivot)) >
        Math.abs(get(augmented, largest, pivot))
      ) {
        largest = i;
      }
    }
    [augmented[pivot], augmented[largest]] = [
      augmented[largest] ?? [],
      augmented[pivot] ?? [],
    ];
    for (let i = pivot + 1; i < n; i++) {
      const factor = get(augmented, i, pivot) / get(augmented, pivot, pivot);
      augmented[i] = (augmented[i] ?? []).map(
        (value, j) => value - factor * get(augmented, pivot, j),
      );
    }
  }
  const x: Matrix = augmented.map(() => [0]);
  for (let i = n - 1; i >= 0; i--) {
    let known = 0;
    for (let j = i + 1; j < n; j++) {
      known += get(augmented, i, j) * get(x, j, 0);
    }
    x[i] = [(get(augmented, i, n) - known) / get(augmented, i, i)];
  }
  return x;
}
