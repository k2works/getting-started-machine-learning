import { join } from "node:path";
import { dataDir } from "../dataset.ts";
import {
  FEATURES,
  TARGET,
  countMissing,
  loadIris,
  prepareIris,
} from "./iris-preprocessing.ts";

const TEST_SIZE = 0.3;
const SEED = 0;

function sum(counts: Record<string, number>): number {
  return Object.values(counts).reduce((total, count) => total + count, 0);
}

export function main(print: (line: string) => void = console.log): void {
  const csvFile = join(dataDir(), "iris.csv");
  const rows = loadIris(csvFile);
  const missing = countMissing(rows, [...FEATURES, TARGET]);
  const split = prepareIris(csvFile, TEST_SIZE, SEED);
  print(`データ件数: ${rows.length}`);
  print(
    `欠損値の数: ${Object.entries(missing)
      .map(([column, count]) => `${column}=${count}`)
      .join(", ")}`,
  );
  print(
    `訓練データ: ${split.xTrain.length} 件, テストデータ: ${split.xTest.length} 件`,
  );
  print(
    `補完後の欠損値の数: 訓練データ ${sum(countMissing(split.xTrain, FEATURES))}, テストデータ ${sum(countMissing(split.xTest, FEATURES))}`,
  );
}

// node src/chapter02/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
