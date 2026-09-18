import { existsSync } from "node:fs";
import { join } from "node:path";
import { beforeAll, describe, expect, it } from "vitest";
import {
  type TrainTestSplit,
  countMissing,
} from "../../src/chapter02/iris-preprocessing.ts";
import {
  joinWeather,
  loadBike,
  loadWeather,
  meanCountByWeather,
} from "../../src/chapter09/bike-weather.ts";
import { prepareBoston, scoreFeatureSet } from "../../src/chapter09/boston.ts";
import { COLUMNS, SQUARES, main } from "../../src/chapter09/main.ts";
import { dataDir } from "../../src/dataset.ts";

const bostonCsv = join(dataDir(), "Boston.csv");
const bikeTsv = join(dataDir(), "bike.tsv");
const weatherCsv = join(dataDir(), "weather.csv");
const hasData = [bostonCsv, bikeTsv, weatherCsv].every((file) =>
  existsSync(file),
);

function totalMissing(rows: readonly Record<string, number>[]): number {
  const columns = Object.keys(rows[0] ?? {});
  return Object.values(countMissing(rows, columns)).reduce(
    (total, count) => total + count,
    0,
  );
}

describe.skipIf(!hasData)(
  "Boston.csv・bike.tsv・weather.csv の実データ",
  () => {
    let split: TrainTestSplit<Record<string, number>, number>;

    beforeAll(() => {
      split = prepareBoston(bostonCsv, 0.3, 0);
    });

    it("実データを 70 件と 30 件に分けて欠損値を補完する", () => {
      expect([split.xTrain.length, split.xTest.length]).toEqual([70, 30]);
      expect(totalMissing(split.xTrain)).toBe(0);
      expect(totalMissing(split.xTest)).toBe(0);
    });

    it("2 乗の項を加えるとテストデータの決定係数が上がる", () => {
      const [, base] = scoreFeatureSet(split, COLUMNS, COLUMNS);
      const [, squares] = scoreFeatureSet(split, COLUMNS, [
        ...COLUMNS,
        ...SQUARES,
      ]);

      expect(base).toBeCloseTo(0.1363, 4);
      expect(squares).toBeCloseTo(0.6205, 4);
    });

    it("天気ごとの平均利用者数を求める", () => {
      const joined = joinWeather(loadBike(bikeTsv), loadWeather(weatherCsv));

      const means = meanCountByWeather(joined);

      expect(joined).toHaveLength(731);
      expect([...means.keys()]).toEqual(["晴れ", "曇り", "雨"]);
      expect([...means.values()].map((mean) => mean.toFixed(1))).toEqual([
        "4876.8",
        "4052.7",
        "1803.3",
      ]);
    });

    it("実行すると特徴量エンジニアリングの結果を表示する", () => {
      const lines: string[] = [];

      main((line) => lines.push(line));

      expect(lines).toEqual([
        "訓練データ: 70 件, テストデータ: 30 件",
        "特徴量の列: ZN, INDUS, CHAS, NOX, RM, AGE, DIS, RAD, TAX, PTRATIO, B, LSTAT, CRIME_low, CRIME_very_low",
        "標準化した訓練データの RM: 平均 0.00, 標準偏差 1.00",
        "決定係数:",
        "  元の特徴量（3 列）: 訓練 0.7859, テスト 0.1363",
        "  2 乗の項を追加（6 列）: 訓練 0.8574, テスト 0.6205",
        "  交互作用の項も追加（9 列）: 訓練 0.8764, テスト -0.3782",
        "訓練データの PRICE の外れ値: 6 件",
        "  外れ値を除いて 2 乗の項を追加: 訓練 0.7014, テスト 0.5599",
        "天気ごとの平均利用者数: 晴れ=4876.8, 曇り=4052.7, 雨=1803.3",
      ]);
    });
  },
);
