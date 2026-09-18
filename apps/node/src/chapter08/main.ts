import { basename, join } from "node:path";
import { splitTrainTest } from "../chapter02/iris-preprocessing.ts";
import { dataDir } from "../dataset.ts";
import { CLASS_WEIGHTS, type ClassWeight } from "./decision-tree-classifier.ts";
import { evaluate } from "./evaluation.ts";
import {
  type FittedPipeline,
  fitPipeline,
  loadModel,
  predict,
  saveModel,
} from "./pipeline.ts";
import {
  type Passenger,
  loadSurvived,
  splitFeaturesAndTarget,
} from "./survived-data.ts";

const TEST_SIZE = 0.2;
const SEED = 0;
const MAX_DEPTH = 5;

/** 学習済みのパイプラインの保存先（apps/node/model/ は .gitignore の対象） */
export const MODEL_FILE = "model/survived.json";

const NEW_PASSENGERS: Passenger[] = [
  {
    Pclass: 1,
    Sex: "female",
    Age: null,
    SibSp: 0,
    Parch: 0,
    Fare: 50,
    Embarked: "C",
  },
  {
    Pclass: 3,
    Sex: "male",
    Age: null,
    SibSp: 0,
    Parch: 0,
    Fare: 8,
    Embarked: "S",
  },
];

export function main(
  print: (line: string) => void = console.log,
  modelFile = MODEL_FILE,
): void {
  const rows = loadSurvived(join(dataDir(), "Survived.csv"));
  const { x, t } = splitFeaturesAndTarget(rows);
  const split = splitTrainTest(x, t, TEST_SIZE, SEED);
  const survivors = t.filter((label) => label === 1).length;
  print(
    `データ件数: ${rows.length}（生存 ${survivors}, 死亡 ${rows.length - survivors}）`,
  );
  print(
    `訓練データ: ${split.xTrain.length} 件, テストデータ: ${split.xTest.length} 件`,
  );

  const pipelines = Object.fromEntries(
    CLASS_WEIGHTS.map((classWeight) => [
      classWeight,
      fitPipeline(split.xTrain, split.tTrain, {
        maxDepth: MAX_DEPTH,
        classWeight,
      }),
    ]),
  ) as Record<ClassWeight, FittedPipeline>;
  for (const classWeight of CLASS_WEIGHTS) {
    const result = evaluate(pipelines[classWeight], split);
    print(
      `classWeight=${classWeight}: 訓練 ${result.trainAccuracy.toFixed(3)}, ` +
        `テスト ${result.testAccuracy.toFixed(3)}, ` +
        `生存者 ${result.survivors} 人中 ${result.foundSurvivors} 人を発見`,
    );
  }

  saveModel(pipelines.balanced, modelFile);
  const predictions = predict(loadModel(modelFile), NEW_PASSENGERS);
  print(`保存したモデル: ${basename(modelFile)}`);
  print(`架空の乗客の予測: [${predictions.join(", ")}]`);
}

// node src/chapter08/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
