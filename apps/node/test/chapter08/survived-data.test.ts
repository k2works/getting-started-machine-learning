import { existsSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { beforeAll, describe, expect, it } from "vitest";
import {
  type TrainTestSplit,
  splitTrainTest,
} from "../../src/chapter02/iris-preprocessing.ts";
import { DecisionTree } from "../../src/chapter03/decision-tree.ts";
import { trainMlCart } from "../../src/chapter03/ml-cart-adapter.ts";
import type { ClassWeight } from "../../src/chapter08/decision-tree-classifier.ts";
import { type Evaluation, evaluate } from "../../src/chapter08/evaluation.ts";
import { main } from "../../src/chapter08/main.ts";
import {
  fitPipeline,
  predict,
  transform,
} from "../../src/chapter08/pipeline.ts";
import {
  type Passenger,
  loadSurvived,
  splitFeaturesAndTarget,
} from "../../src/chapter08/survived-data.ts";
import { dataDir } from "../../src/dataset.ts";

const csvFile = join(dataDir(), "Survived.csv");

describe.skipIf(!existsSync(csvFile))("Survived.csv の実データ", () => {
  let split: TrainTestSplit<Passenger, number>;

  beforeAll(() => {
    const { x, t } = splitFeaturesAndTarget(loadSurvived(csvFile));
    split = splitTrainTest(x, t, 0.2, 0);
  });

  function evaluateWith(
    maxDepth: number,
    classWeight: ClassWeight,
  ): Evaluation {
    const options = { maxDepth, classWeight };
    return evaluate(fitPipeline(split.xTrain, split.tTrain, options), split);
  }

  /** 重み付けなしの自作の木と、前処理後のデータで学習した別の木の予測が違う件数 */
  function countDifferences(
    maxDepth: number,
    train: (
      x: Record<string, number>[],
      t: string[],
    ) => (rows: Record<string, number>[]) => string[],
  ): number {
    const options = { maxDepth, classWeight: "none" as const };
    const pipeline = fitPipeline(split.xTrain, split.tTrain, options);
    const xTrain = transform(pipeline, split.xTrain);
    const xTest = transform(pipeline, split.xTest);
    const other = train(xTrain, split.tTrain.map(String))(xTest);
    return predict(pipeline, split.xTest).filter(
      (label, i) => String(label) !== other[i],
    ).length;
  }

  it("実データの件数と欠損値の数を確認する", () => {
    const rows = loadSurvived(csvFile);

    const missing = (["Age", "Cabin", "Embarked"] as const).map(
      (column) => rows.filter((row) => row[column] === null).length,
    );

    expect(rows).toHaveLength(891);
    expect(missing).toEqual([177, 687, 2]);
  });

  it("深さ 5 では balanced にすると見つけられる生存者が増える", () => {
    expect(evaluateWith(5, "none").foundSurvivors).toBe(40);
    expect(evaluateWith(5, "balanced").foundSurvivors).toBe(50);
  });

  it("深さ 4 では balanced にしても評価が変わらない", () => {
    expect(evaluateWith(4, "balanced")).toEqual(evaluateWith(4, "none"));
  });

  it.each([1, 2, 3, 4, 5, 6])(
    "深さ %s では ml-cart と前処理後のテストデータの予測が一致する",
    (maxDepth) => {
      const mlCart = (x: Record<string, number>[], t: string[]) =>
        trainMlCart(x, t, { maxDepth });

      expect(countDifferences(maxDepth, mlCart)).toBe(0);
    },
  );

  it("深さ 7 では ml-cart と 8 件の予測が違う", () => {
    const mlCart = (x: Record<string, number>[], t: string[]) =>
      trainMlCart(x, t, { maxDepth: 7 });

    expect(countDifferences(7, mlCart)).toBe(8);
  });

  it("重み付けなしなら深さ 10 まで第 3 章の決定木と予測が一致する", () => {
    for (let maxDepth = 1; maxDepth <= 10; maxDepth++) {
      const chapter03 = (x: Record<string, number>[], t: string[]) => {
        const tree = new DecisionTree({ maxDepth }).fit(x, t);
        return (rows: Record<string, number>[]) => tree.predict(rows);
      };

      expect(countDifferences(maxDepth, chapter03)).toBe(0);
    }
  });

  it("実行すると評価結果を表示してモデルを保存する", () => {
    const modelFile = join(
      mkdtempSync(join(tmpdir(), "model-")),
      "survived.json",
    );
    const lines: string[] = [];

    main((line) => lines.push(line), modelFile);

    expect(existsSync(modelFile)).toBe(true);
    expect(lines).toEqual([
      "データ件数: 891（生存 342, 死亡 549）",
      "訓練データ: 712 件, テストデータ: 179 件",
      "classWeight=none: 訓練 0.857, テスト 0.782, 生存者 67 人中 40 人を発見",
      "classWeight=balanced: 訓練 0.836, テスト 0.743, 生存者 67 人中 50 人を発見",
      "保存したモデル: survived.json",
      "架空の乗客の予測: [1, 0]",
    ]);
  });
});
