import { existsSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  fitLinearRegression,
  loadCinema,
  predict,
  prepareCinema,
  removeOutliers,
} from "../../src/chapter07/cinema-regression.ts";
import { main } from "../../src/chapter07/main.ts";
import { trainMlLinearRegression } from "../../src/chapter07/ml-regression-adapter.ts";
import { r2Score } from "../../src/chapter07/regression-metrics.ts";
import { dataDir } from "../../src/dataset.ts";

const csvFile = join(dataDir(), "cinema.csv");

describe.skipIf(!existsSync(csvFile))("cinema.csv の実データ", () => {
  it("実データから外れ値を 1 件取り除く", () => {
    const rows = loadCinema(csvFile);

    expect([rows.length, removeOutliers(rows).length]).toEqual([100, 99]);
  });

  it("実データで自作のモデルと ml-regression-multivariate-linear の R2 が一致する", () => {
    const split = prepareCinema(csvFile, 0.2, 0);

    const mine = predict(
      fitLinearRegression(split.xTrain, split.tTrain),
      split.xTest,
    );
    const library = predict(
      trainMlLinearRegression(split.xTrain, split.tTrain),
      split.xTest,
    );

    expect(r2Score(split.tTest, library)).toBeCloseTo(
      r2Score(split.tTest, mine),
      9,
    );
  });

  it("実行すると学習した係数と評価指標を表示する", () => {
    const lines: string[] = [];

    main((line) => lines.push(line));

    expect(lines).toEqual([
      "データ件数: 100",
      "外れ値を除いた件数: 99",
      "訓練データ: 79 件, テストデータ: 20 件",
      "切片: 6299.63",
      "係数: SNS1=1.0876, SNS2=0.4358, actor=0.2785, original=282.5378",
      "テストデータの評価: R2=0.8124, MAE=238.01, RMSE=305.15",
    ]);
  });
});
