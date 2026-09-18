import { kmeans } from "ml-kmeans";
import { type KMeansResult, type Point, sumOfSquaredErrors } from "./kmeans.ts";

const MAX_ITERATIONS = 300;

// ml-kmeans は書き換え可能な number[][] を受け取るので、読み取り専用の点を写す
function toMatrix(points: readonly Point[]): number[][] {
  return points.map((point) => [...point]);
}

/** ml-kmeans に初期中心を渡して学習し、自作の kmeans と同じ形の結果にする。 */
export function trainMlKMeans(
  points: readonly Point[],
  initialCenters: readonly Point[],
): KMeansResult {
  const result = kmeans(toMatrix(points), initialCenters.length, {
    initialization: toMatrix(initialCenters),
    maxIterations: MAX_ITERATIONS,
  });
  return {
    labels: result.clusters,
    centers: result.centroids,
    sse: sumOfSquaredErrors(points, result.clusters, result.centroids),
    iterations: result.iterations,
  };
}

/** ml-kmeans の k-means++ をシードを変えて nInit 回学習し、最小の SSE を返す。 */
export function mlKMeansBestSse(
  points: readonly Point[],
  nClusters: number,
  seed: number,
  nInit = 10,
): number {
  const matrix = toMatrix(points);
  const sses = Array.from({ length: nInit }, (_, i) => {
    const result = kmeans(matrix, nClusters, {
      initialization: "kmeans++",
      seed: seed + i,
      maxIterations: MAX_ITERATIONS,
    });
    return sumOfSquaredErrors(points, result.clusters, result.centroids);
  });
  return Math.min(...sses);
}
