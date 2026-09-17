import { join } from "node:path";
import { dataDir } from "../dataset.ts";
import {
  accuracy,
  loadPeople,
  predictByRule,
  splitFeaturesAndLabels,
} from "./kinoko-takenoko.ts";

export function main(print: (line: string) => void = console.log): void {
  const people = loadPeople(join(dataDir(), "KvsT.csv"));
  const { features, labels } = splitFeaturesAndLabels(people);
  const predictions = features.map(predictByRule);
  print(`データ件数: ${people.length}`);
  print(
    `ルールによる判定の正解率: ${accuracy(predictions, labels).toFixed(4)}`,
  );
}

// node src/chapter01/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
