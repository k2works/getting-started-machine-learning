import { createRandom, shuffle } from "../chapter02/random.ts";

/** 1 つの点。座標（特徴量）の値を並べた配列。 */
export type Point = readonly number[];

export function squaredDistance(a: Point, b: Point): number {
  return a.reduce((sum, x, j) => sum + (x - (b[j] as number)) ** 2, 0);
}

/** 値が最小になる要素の番号。同じ値なら先に現れた番号を返す。 */
function argmin(values: readonly number[]): number {
  return values.reduce(
    (best, value, i) => (value < (values[best] as number) ? i : best),
    0,
  );
}

export function assignClusters(
  points: readonly Point[],
  centers: readonly Point[],
): number[] {
  return points.map((point) =>
    argmin(centers.map((center) => squaredDistance(point, center))),
  );
}

export function mean(values: readonly number[]): number {
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

export function updateCenters(
  points: readonly Point[],
  labels: readonly number[],
  previousCenters: readonly Point[],
): Point[] {
  return previousCenters.map((previous, k) => {
    const members = points.filter((_, i) => labels[i] === k);
    if (members.length === 0) {
      return previous;
    }
    return previous.map((_, j) => mean(members.map((p) => p[j] as number)));
  });
}

export function sumOfSquaredErrors(
  points: readonly Point[],
  labels: readonly number[],
  centers: readonly Point[],
): number {
  return points.reduce(
    (sum, point, i) =>
      sum + squaredDistance(point, centers[labels[i] as number] as Point),
    0,
  );
}

export interface KMeansResult {
  labels: number[];
  centers: readonly Point[];
  sse: number;
  /** 中心を更新した回数（中心が変わらなかった最後の更新を含む） */
  iterations: number;
}

function samePoints(a: readonly Point[], b: readonly Point[]): boolean {
  return a.every((point, k) => point.every((x, j) => x === b[k]?.[j]));
}

export function kmeans(
  points: readonly Point[],
  initialCenters: readonly Point[],
  { maxIterations = 300 }: { maxIterations?: number } = {},
): KMeansResult {
  let centers = initialCenters;
  let iterations = 0;
  while (iterations < maxIterations) {
    const next = updateCenters(
      points,
      assignClusters(points, centers),
      centers,
    );
    iterations++;
    const converged = samePoints(next, centers);
    centers = next;
    if (converged) {
      break;
    }
  }
  const labels = assignClusters(points, centers);
  const sse = sumOfSquaredErrors(points, labels, centers);
  return { labels, centers, sse, iterations };
}

export function chooseInitialCenters(
  points: readonly Point[],
  nClusters: number,
  seed: number,
): Point[] {
  const indices = points.map((_, i) => i);
  return shuffle(indices, createRandom(seed))
    .slice(0, nClusters)
    .map((i) => points[i] as Point);
}

export function bestKMeans(
  points: readonly Point[],
  initialCenterCandidates: readonly (readonly Point[])[],
): KMeansResult {
  return initialCenterCandidates
    .map((initialCenters) => kmeans(points, initialCenters))
    .reduce((best, result) => (result.sse < best.sse ? result : best));
}

export function kmeansWithRestarts(
  points: readonly Point[],
  nClusters: number,
  seed: number,
  nInit = 10,
): KMeansResult {
  const candidates = Array.from({ length: nInit }, (_, i) =>
    chooseInitialCenters(points, nClusters, seed + i),
  );
  return bestKMeans(points, candidates);
}

export function sseByClusterCount(
  points: readonly Point[],
  clusterCounts: readonly number[],
  seed: number,
  nInit = 10,
): Map<number, number> {
  return new Map(
    clusterCounts.map((n) => [
      n,
      kmeansWithRestarts(points, n, seed, nInit).sse,
    ]),
  );
}
