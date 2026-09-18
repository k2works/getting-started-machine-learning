import { join } from "node:path";
import { isDeepStrictEqual } from "node:util";
import { dataDir } from "../dataset.ts";
import {
  chooseInitialCenters,
  kmeans,
  kmeansWithRestarts,
  sseByClusterCount,
} from "./kmeans.ts";
import { mlKMeansBestSse, trainMlKMeans } from "./ml-kmeans-adapter.ts";
import {
  loadSpending,
  SPENDING_COLUMNS,
  standardize,
  summarizeClusters,
} from "./spending.ts";

const SEED = 0;
const N_INIT = 10;
const CLUSTER_COUNTS = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
const N_CLUSTERS = 5;

export function main(print: (line: string) => void = console.log): void {
  const rows = loadSpending(join(dataDir(), "Wholesale.csv"));
  const points = standardize(rows, SPENDING_COLUMNS);
  print(`データ件数: ${rows.length}（支出額 ${SPENDING_COLUMNS.length} 列）`);
  print(`クラスタ数ごとの SSE（初期中心 ${N_INIT} 通りの最小値）:`);
  print("クラスタ数\t自作\tml-kmeans（k-means++）");
  for (const [n, sse] of sseByClusterCount(
    points,
    CLUSTER_COUNTS,
    SEED,
    N_INIT,
  )) {
    const library = mlKMeansBestSse(points, n, SEED, N_INIT);
    print(`${n}\t${sse.toFixed(2)}\t${library.toFixed(2)}`);
  }

  // 自作の試行と同じ初期中心を ml-kmeans に渡し、結果がすべて一致するかを数える
  const initialCenterCandidates = CLUSTER_COUNTS.flatMap((n) =>
    Array.from({ length: N_INIT }, (_, i) =>
      chooseInitialCenters(points, n, SEED + i),
    ),
  );
  const matched = initialCenterCandidates.filter((initialCenters) =>
    isDeepStrictEqual(
      kmeans(points, initialCenters),
      trainMlKMeans(points, initialCenters),
    ),
  ).length;
  print(
    `同じ初期中心で ml-kmeans と結果が一致した数: ${matched} / ${initialCenterCandidates.length}`,
  );

  const result = kmeansWithRestarts(points, N_CLUSTERS, SEED, N_INIT);
  print("");
  print(`クラスタ数 ${N_CLUSTERS} のクラスタごとの件数と平均支出額:`);
  print(["クラスタ", "件数", ...SPENDING_COLUMNS].join("\t"));
  for (const summary of summarizeClusters(
    rows,
    SPENDING_COLUMNS,
    result.labels,
  )) {
    const means = SPENDING_COLUMNS.map((column) =>
      summary.means[column].toFixed(0),
    );
    print([summary.cluster, summary.count, ...means].join("\t"));
  }
}

// node src/chapter14/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
