import { join } from "node:path";
import { accuracy } from "../chapter01/kinoko-takenoko.ts";
import { prepareIris } from "../chapter02/iris-preprocessing.ts";
import { dataDir } from "../dataset.ts";
import { DecisionTree, formatTree } from "./decision-tree.ts";

const TEST_SIZE = 0.3;
const SEED = 0;
const MAX_DEPTHS = [1, 2, 3, 4, 5, undefined];
const TREE_DEPTH_TO_SHOW = 2;

export function main(print: (line: string) => void = console.log): void {
  const split = prepareIris(join(dataDir(), "iris.csv"), TEST_SIZE, SEED);
  print("深さ\t訓練データ\tテストデータ");
  for (const maxDepth of MAX_DEPTHS) {
    const model = new DecisionTree({ maxDepth }).fit(
      split.xTrain,
      split.tTrain,
    );
    const train = accuracy(model.predict(split.xTrain), split.tTrain);
    const test = accuracy(model.predict(split.xTest), split.tTest);
    print(`${maxDepth ?? "制限なし"}\t${train.toFixed(4)}\t${test.toFixed(4)}`);
  }

  const shallow = new DecisionTree({ maxDepth: TREE_DEPTH_TO_SHOW }).fit(
    split.xTrain,
    split.tTrain,
  );
  if (shallow.tree !== null) {
    print("");
    print(`深さ ${TREE_DEPTH_TO_SHOW} の決定木:`);
    for (const line of formatTree(shallow.tree).split("\n")) {
      print(line);
    }
  }
}

// node src/chapter03/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
