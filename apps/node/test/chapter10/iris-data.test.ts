import { existsSync } from "node:fs";
import { join } from "node:path";
import { beforeAll, describe, expect, it } from "vitest";
import {
  type Feature,
  type TrainTestSplit,
  prepareIris,
} from "../../src/chapter02/iris-preprocessing.ts";
import { evaluate } from "../../src/chapter10/classifier.ts";
import { LogisticRegression } from "../../src/chapter10/logistic-regression.ts";
import { main } from "../../src/chapter10/main.ts";
import {
  mlLogisticRegression,
  mlRandomForest,
} from "../../src/chapter10/ml-classifier.ts";
import { RandomForest } from "../../src/chapter10/random-forest.ts";
import { dataDir } from "../../src/dataset.ts";

const csvFile = join(dataDir(), "iris.csv");

// ml-logistic-regression は既定で 50000 回繰り返すので、1 回の学習に数秒かかる
describe.skipIf(!existsSync(csvFile))(
  "iris.csv の実データ",
  { timeout: 30_000 },
  () => {
    let split: TrainTestSplit<Record<Feature, number>, string>;

    beforeAll(() => {
      split = prepareIris(csvFile, 0.3, 0);
    });

    it("ロジスティック回帰はテストデータの 45 件中 42 件を正しく分類する", () => {
      const score = evaluate(new LogisticRegression(), split);

      expect(score.test).toBeCloseTo(42 / 45, 12);
    });

    it("ランダムフォレストは訓練データを分け切りテストデータの 45 件中 42 件を正しく分類する", () => {
      const score = evaluate(
        new RandomForest({ nEstimators: 100, maxFeatures: 2, seed: 0 }),
        split,
      );

      expect(score.train).toBe(1);
      expect(score.test).toBeCloseTo(42 / 45, 12);
    });

    it("ml-logistic-regression は切片が無いとテストデータの 45 件中 26 件しか正しく分類できない", () => {
      const score = evaluate(mlLogisticRegression(), split);

      expect(score.test).toBeCloseTo(26 / 45, 12);
    });

    it("ml-logistic-regression は値が 1 の列を足すとテストデータの 45 件中 43 件を正しく分類する", () => {
      const score = evaluate(mlLogisticRegression({ intercept: true }), split);

      expect(score.test).toBeCloseTo(43 / 45, 12);
    });

    it("ml-random-forest は訓練データを分け切りテストデータの 45 件中 44 件を正しく分類する", () => {
      const score = evaluate(
        mlRandomForest({ nEstimators: 100, maxFeatures: 2, seed: 0 }),
        split,
      );

      expect(score.train).toBe(1);
      expect(score.test).toBeCloseTo(44 / 45, 12);
    });

    it("実行するとモデルごとの正解率とランダムフォレストの重要度を表示する", () => {
      const lines: string[] = [];

      main((line) => lines.push(line));

      expect(lines).toEqual([
        "モデル\t訓練データ\tテストデータ",
        "決定木（深さ 2）\t0.9238\t0.9778",
        "ロジスティック回帰\t0.8952\t0.9333",
        "ランダムフォレスト（100 本）\t1.0000\t0.9333",
        "ランダムフォレスト（100 本・深さ 2）\t0.9333\t0.9778",
        "ml-logistic-regression（切片なし）\t0.7048\t0.5778",
        "ml-logistic-regression（値が 1 の列を追加）\t0.8857\t0.9556",
        "ml-random-forest（100 本）\t1.0000\t0.9778",
        "",
        "ランダムフォレスト（100 本）の特徴量の重要度:",
        "がく片長さ\t0.2200",
        "がく片幅\t0.1271",
        "花弁長さ\t0.2505",
        "花弁幅\t0.4024",
      ]);
    });
  },
);
