import { existsSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import {
  FEATURES,
  TARGET,
  countMissing,
  loadIris,
  prepareIris,
} from "../../src/chapter02/iris-preprocessing.ts";
import { main } from "../../src/chapter02/main.ts";
import { dataDir } from "../../src/dataset.ts";

const csvFile = join(dataDir(), "iris.csv");

describe.skipIf(!existsSync(csvFile))("iris.csv の実データ", () => {
  it("実データの列ごとの欠損値の数を数える", () => {
    expect(countMissing(loadIris(csvFile), [...FEATURES, TARGET])).toEqual({
      がく片長さ: 2,
      がく片幅: 1,
      花弁長さ: 2,
      花弁幅: 2,
      種類: 0,
    });
  });

  it("実データを 105 件と 45 件に分けて欠損値を補完する", () => {
    const split = prepareIris(csvFile, 0.3, 0);

    expect([split.xTrain.length, split.xTest.length]).toEqual([105, 45]);
    expect(Object.values(countMissing(split.xTrain, FEATURES))).toEqual([
      0, 0, 0, 0,
    ]);
    expect(Object.values(countMissing(split.xTest, FEATURES))).toEqual([
      0, 0, 0, 0,
    ]);
  });

  it("実行すると前処理の結果を表示する", () => {
    const lines: string[] = [];

    main((line) => lines.push(line));

    expect(lines).toEqual([
      "データ件数: 150",
      "欠損値の数: がく片長さ=2, がく片幅=1, 花弁長さ=2, 花弁幅=2, 種類=0",
      "訓練データ: 105 件, テストデータ: 45 件",
      "補完後の欠損値の数: 訓練データ 0, テストデータ 0",
    ]);
  });
});
