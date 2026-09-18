import { existsSync } from "node:fs";
import { join } from "node:path";
import { beforeAll, describe, expect, it } from "vitest";
import {
  type BostonDataset,
  prepareBoston,
} from "../../src/chapter12/boston-features.ts";
import { fitLasso } from "../../src/chapter12/lasso.ts";
import { main } from "../../src/chapter12/main.ts";
import { fitRidge } from "../../src/chapter12/regularization.ts";
import { dataDir } from "../../src/dataset.ts";

const csvFile = join(dataDir(), "Boston.csv");

describe.skipIf(!existsSync(csvFile))("Boston.csv の実データ", () => {
  let dataset: BostonDataset;

  beforeAll(() => {
    dataset = prepareBoston(csvFile, 0.3, 0.3, 0);
  });

  it("外れ値を除いて訓練データと検証データとテストデータに分ける", () => {
    expect(
      [dataset.tTrain, dataset.tValid, dataset.tTest].map((t) => t.length),
    ).toEqual([47, 21, 30]);
  });

  it("lambda が 0 のラッソ回帰は、自作の最小二乗法と同じ係数と切片になる", () => {
    const lasso = fitLasso(dataset.xTrain, dataset.tTrain, 0);
    const linear = fitRidge(dataset.xTrain, dataset.tTrain, 0);

    expect(lasso.coefficients).toEqual(
      linear.coefficients.map((c) => expect.closeTo(c, 6)),
    );
    expect(lasso.intercept).toBeCloseTo(linear.intercept, 6);
  });

  it("実行すると正則化の実験結果を表示する", () => {
    const lines: string[] = [];

    main((line) => lines.push(line));

    expect(lines).toEqual([
      "データ件数: 98（外れ値 2 件を除外）",
      "訓練データ: 47 件, 検証データ: 21 件, テストデータ: 30 件",
      "特徴量: RM, PTRATIO, LSTAT, RM^2, RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2",
      "alpha\t訓練 R²\t検証 R²\t係数の絶対値の合計",
      "0\t0.8928\t0.7180\t20.570",
      "0.1\t0.8928\t0.7224\t20.050",
      "1\t0.8912\t0.7444\t17.147",
      "10\t0.8721\t0.7537\t12.526",
      "100\t0.6923\t0.6076\t6.751",
      "検証データで選んだ alpha: 10",
      "テストデータの決定係数: 線形回帰 0.2242, リッジ回帰 0.5704",
      "ラッソ回帰（lambda=0.05）で係数が 0 になった特徴量: RM PTRATIO, RM LSTAT, PTRATIO^2, PTRATIO LSTAT, LSTAT^2",
      "",
      "シード\t選んだ alpha\t線形回帰\tリッジ回帰",
      "0\t10\t0.2242\t0.5704",
      "1\t10\t0.6491\t0.7522",
      "2\t10\t0.7926\t0.7825",
      "3\t10\t0.7543\t0.8086",
      "4\t100\t0.3684\t0.5670",
    ]);
  });
});
