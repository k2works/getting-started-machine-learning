import { join } from "node:path";
import { dataDir } from "../dataset.ts";
import {
  joinWeather,
  loadBike,
  loadWeather,
  meanCountByWeather,
} from "./bike-weather.ts";
import { prepareBoston, scoreFeatureSet } from "./boston.ts";
import { iqrOutliers, removeTargetOutliers } from "./outliers.ts";
import { pairsWithReplacement, termName } from "./polynomial-features.ts";
import { Standardizer } from "./standardizer.ts";

const TEST_SIZE = 0.3;
const SEED = 0;
// 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす
const ZERO_TOLERANCE = 1e-9;
const SCORE_DIGITS = 4;
const MEAN_DIGITS = 2;
const COUNT_DIGITS = 1;

export const COLUMNS = ["RM", "LSTAT", "PTRATIO"];
export const SQUARES = ["RM^2", "LSTAT^2", "PTRATIO^2"];
export const FEATURE_SETS = new Map([
  ["元の特徴量", COLUMNS],
  ["2 乗の項を追加", [...COLUMNS, ...SQUARES]],
  [
    "交互作用の項も追加",
    [
      ...COLUMNS,
      ...pairsWithReplacement(COLUMNS).map(([left, right]) =>
        termName(left, right),
      ),
    ],
  ],
]);

function format(value: number, digits: number): string {
  return (Math.abs(value) < ZERO_TOLERANCE ? 0 : value).toFixed(digits);
}

function formatScores([train, test]: [number, number]): string {
  return `訓練 ${format(train, SCORE_DIGITS)}, テスト ${format(test, SCORE_DIGITS)}`;
}

export function main(print: (line: string) => void = console.log): void {
  const split = prepareBoston(join(dataDir(), "Boston.csv"), TEST_SIZE, SEED);
  print(
    `訓練データ: ${split.xTrain.length} 件, テストデータ: ${split.xTest.length} 件`,
  );
  print(`特徴量の列: ${Object.keys(split.xTrain[0] ?? {}).join(", ")}`);

  // 標準化した後の平均と標準偏差も、Standardizer.fit で求める
  const standardized = Standardizer.fit(split.xTrain, ["RM"]).transform(
    split.xTrain,
  );
  const rm = Standardizer.fit(standardized, ["RM"]);
  print(
    `標準化した訓練データの RM: 平均 ${format(rm.means.RM, MEAN_DIGITS)}, 標準偏差 ${format(rm.stds.RM, MEAN_DIGITS)}`,
  );

  print("決定係数:");
  for (const [name, terms] of FEATURE_SETS) {
    print(
      `  ${name}（${terms.length} 列）: ${formatScores(scoreFeatureSet(split, COLUMNS, terms))}`,
    );
  }

  const outliers = iqrOutliers(split.tTrain).filter((outlier) => outlier);
  print(`訓練データの PRICE の外れ値: ${outliers.length} 件`);
  const removed = scoreFeatureSet(removeTargetOutliers(split), COLUMNS, [
    ...COLUMNS,
    ...SQUARES,
  ]);
  print(`  外れ値を除いて 2 乗の項を追加: ${formatScores(removed)}`);

  const joined = joinWeather(
    loadBike(join(dataDir(), "bike.tsv")),
    loadWeather(join(dataDir(), "weather.csv")),
  );
  const means = [...meanCountByWeather(joined)]
    .map(([weather, count]) => `${weather}=${format(count, COUNT_DIGITS)}`)
    .join(", ");
  print(`天気ごとの平均利用者数: ${means}`);
}

// node src/chapter09/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
