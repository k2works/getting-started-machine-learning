import { EVD, Matrix } from "ml-matrix";

/** 学習した主成分分析のモデル。components の 1 行が 1 つの主成分を表す */
export interface PcaModel {
  mean: number[];
  components: number[][];
  explainedVariance: number[];
  explainedVarianceRatio: number[];
}

export function columnMeans(x: number[][]): number[] {
  return new Matrix(x).mean("column");
}

/** 各行から列の平均を引く（元の配列は変えない） */
function center(x: number[][], means: number[]): Matrix {
  return new Matrix(x).subRowVector(means);
}

export function covarianceMatrix(x: number[][]): number[][] {
  const centered = center(x, columnMeans(x));
  return centered
    .transpose()
    .mmul(centered)
    .div(x.length - 1)
    .to2DArray();
}

/** 固有ベクトルは符号が逆でも同じ向きを表すので、絶対値が最大の要素が正になるようにそろえる */
export function normalizeSigns(components: number[][]): number[][] {
  return components.map((row) => {
    const largest = row.reduce((a, b) => (Math.abs(b) > Math.abs(a) ? b : a));
    return row.map((value) => value * Math.sign(largest));
  });
}

export function fitPca(x: number[][], nComponents: number): PcaModel {
  const evd = new EVD(covarianceMatrix(x));
  const eigenvalues = evd.realEigenvalues;
  const total = eigenvalues.reduce((sum, value) => sum + value, 0);
  // EVD は固有値を小さい順に返すので、後ろから大きい順に nComponents 個を選ぶ
  const selected = eigenvalues
    .map((_, i) => i)
    .reverse()
    .slice(0, nComponents);
  const variances = selected.map((i) => eigenvalues[i] as number);
  return {
    mean: columnMeans(x),
    components: normalizeSigns(
      selected.map((i) => evd.eigenvectorMatrix.getColumn(i)),
    ),
    explainedVariance: variances,
    explainedVarianceRatio: variances.map((value) => value / total),
  };
}

export function transform(model: PcaModel, x: number[][]): number[][] {
  return center(x, model.mean)
    .mmul(new Matrix(model.components).transpose())
    .to2DArray();
}

/** 先頭から順に足した途中経過を並べる（[0.5, 0.25, 0.25] なら [0.5, 0.75, 1]） */
function cumulativeSum(values: number[]): number[] {
  return values.reduce<number[]>(
    (sums, value) => [...sums, (sums.at(-1) ?? 0) + value],
    [],
  );
}

export function componentsNeeded(ratios: number[], threshold: number): number {
  return cumulativeSum(ratios).findIndex((sum) => sum >= threshold) + 1;
}

export function topLoadings(
  component: number[],
  columns: string[],
  k: number,
): [string, number][] {
  return columns
    .map((column, i): [string, number] => [column, component[i] as number])
    .sort(([, a], [, b]) => Math.abs(b) - Math.abs(a))
    .slice(0, k);
}
