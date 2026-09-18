import { existsSync } from "node:fs";
import { join } from "node:path";
import { ConfusionMatrix } from "ml-confusion-matrix";
import { describe, expect, it } from "vitest";
import { loadSurvived, prepareSurvived } from "../../src/chapter11/datasets.ts";
import { kFold } from "../../src/chapter11/cross-validation.ts";
import {
  N_SPLITS,
  SEED,
  SURVIVED_METRICS,
  evaluate,
  evaluateCinema,
  evaluateSurvived,
} from "../../src/chapter11/experiments.ts";
import { main } from "../../src/chapter11/main.ts";
import { mean } from "../../src/chapter11/metrics.ts";
import { dataDir } from "../../src/dataset.ts";
import { MlCartTree } from "./ml-cart-model.ts";

const survivedCsv = join(dataDir(), "Survived.csv");
const cinemaCsv = join(dataDir(), "cinema.csv");

function expectScores(
  actual: Record<string, number>,
  expected: Record<string, number>,
  digits: number,
): void {
  expect(Object.keys(actual)).toEqual(Object.keys(expected));
  for (const [name, value] of Object.entries(expected)) {
    expect(actual[name], name).toBeCloseTo(value, digits);
  }
}

describe.skipIf(!existsSync(survivedCsv))("Survived.csv の実データ", () => {
  it("決定木を 5 分割交差検証で評価する", () => {
    expectScores(
      evaluateSurvived(survivedCsv),
      { 正解率: 0.7845, 適合率: 0.7909, 再現率: 0.628, F値: 0.6839 },
      4,
    );
  });

  it("同じ分割なら ml-cart の決定木を ml-confusion-matrix で採点した平均と一致する", () => {
    const { x, t } = prepareSurvived(loadSurvived(survivedCsv));
    const folds = kFold(x.length, N_SPLITS, SEED);

    const evaluations = folds.map((fold) => {
      const model = new MlCartTree(2);
      model.fit(
        fold.train.map((i) => x[i] as (typeof x)[number]),
        fold.train.map((i) => t[i] as string),
      );
      const predicted = model.predict(
        fold.test.map((i) => x[i] as (typeof x)[number]),
      );
      return ConfusionMatrix.fromLabels(
        fold.test.map((i) => t[i] as string),
        predicted,
      );
    });

    expectScores(
      evaluate(() => new MlCartTree(2), x, t, SURVIVED_METRICS),
      {
        正解率: mean(evaluations.map((cm) => cm.getAccuracy())),
        適合率: mean(
          evaluations.map((cm) => cm.getPositivePredictiveValue("1")),
        ),
        再現率: mean(evaluations.map((cm) => cm.getTruePositiveRate("1"))),
        F値: mean(evaluations.map((cm) => cm.getF1Score("1"))),
      },
      12,
    );
  });

  it("深さ 2 では自作の決定木と ml-cart の決定木の交差検証の平均が一致する", () => {
    const { x, t } = prepareSurvived(loadSurvived(survivedCsv));

    expectScores(
      evaluateSurvived(survivedCsv),
      evaluate(() => new MlCartTree(2), x, t, SURVIVED_METRICS),
      12,
    );
  });
});

describe.skipIf(!existsSync(cinemaCsv))("cinema.csv の実データ", () => {
  it("線形回帰を 5 分割交差検証で評価する", () => {
    expectScores(evaluateCinema(cinemaCsv), { RMSE: 411.02, MAE: 327.89 }, 2);
  });
});

describe.skipIf(!existsSync(survivedCsv) || !existsSync(cinemaCsv))(
  "第 11 章の実行",
  () => {
    it("実行すると交差検証の平均を表示する", () => {
      const lines: string[] = [];

      main((line) => lines.push(line));

      expect(lines).toEqual([
        "Survived（決定木、5 分割交差検証の平均）",
        "  正解率: 0.7845",
        "  適合率: 0.7909",
        "  再現率: 0.6280",
        "  F値: 0.6839",
        "cinema（線形回帰、5 分割交差検証の平均）",
        "  RMSE: 411.02",
        "  MAE: 327.89",
      ]);
    });
  },
);
