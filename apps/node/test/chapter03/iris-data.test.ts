import { existsSync } from "node:fs";
import { join } from "node:path";
import { beforeAll, describe, expect, it } from "vitest";
import { accuracy } from "../../src/chapter01/kinoko-takenoko.ts";
import {
  type Feature,
  type TrainTestSplit,
  prepareIris,
} from "../../src/chapter02/iris-preprocessing.ts";
import { DecisionTree } from "../../src/chapter03/decision-tree.ts";
import { main } from "../../src/chapter03/main.ts";
import { trainMlCart } from "../../src/chapter03/ml-cart-adapter.ts";
import { dataDir } from "../../src/dataset.ts";

const csvFile = join(dataDir(), "iris.csv");

function countDifferences(
  split: TrainTestSplit<Record<Feature, number>, string>,
  maxDepth: number | undefined,
): number {
  const mine = new DecisionTree({ maxDepth })
    .fit(split.xTrain, split.tTrain)
    .predict(split.xTest);
  const library = trainMlCart(split.xTrain, split.tTrain, { maxDepth })(
    split.xTest,
  );
  return mine.filter((label, i) => label !== library[i]).length;
}

describe.skipIf(!existsSync(csvFile))("iris.csv の実データ", () => {
  let split: TrainTestSplit<Record<Feature, number>, string>;

  beforeAll(() => {
    split = prepareIris(csvFile, 0.3, 0);
  });

  it("深さ 2 の決定木はテストデータの 45 件中 44 件を正しく分類する", () => {
    const predictions = new DecisionTree({ maxDepth: 2 })
      .fit(split.xTrain, split.tTrain)
      .predict(split.xTest);

    expect(accuracy(predictions, split.tTest)).toBeCloseTo(44 / 45, 12);
  });

  it.each([1, 2, 4, 5, undefined])(
    "深さ %s では ml-cart とテストデータの予測が一致する",
    (maxDepth) => {
      expect(countDifferences(split, maxDepth)).toBe(0);
    },
  );

  it("深さ 3 では多数決が同数の葉に落ちる 1 件だけ ml-cart と予測が違う", () => {
    expect(countDifferences(split, 3)).toBe(1);
  });

  it("実行すると深さごとの正解率と深さ 2 の決定木を表示する", () => {
    const lines: string[] = [];

    main((line) => lines.push(line));

    expect(lines).toEqual([
      "深さ\t訓練データ\tテストデータ",
      "1\t0.6952\t0.6000",
      "2\t0.9238\t0.9778",
      "3\t0.9429\t0.9556",
      "4\t0.9524\t0.9556",
      "5\t0.9810\t0.9111",
      "制限なし\t1.0000\t0.9333",
      "",
      "深さ 2 の決定木:",
      "花弁幅 <= 0.2750",
      "  Iris-setosa",
      "花弁幅 > 0.2750",
      "  花弁幅 <= 0.6900",
      "    Iris-versicolor",
      "  花弁幅 > 0.6900",
      "    Iris-virginica",
    ]);
  });
});
