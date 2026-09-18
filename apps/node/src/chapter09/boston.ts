import { readFileSync } from "node:fs";
import { parse } from "csv-parse/sync";
import {
  type TrainTestSplit,
  columnMeans,
  fillMissing,
  splitTrainTest,
} from "../chapter02/iris-preprocessing.ts";
import { dummyCategories, encodeDummies } from "./dummies.ts";
import { fitLinearRegression, rSquared } from "./linear-model.ts";
import { polynomialFeatures } from "./polynomial-features.ts";
import { Standardizer } from "./standardizer.ts";

const TARGET = "PRICE";
const CATEGORY = "CRIME";

type BostonRow = Record<typeof CATEGORY, string> &
  Record<string, string | number | null>;

function loadBoston(csvFile: string): BostonRow[] {
  return parse<BostonRow>(readFileSync(csvFile), {
    columns: true,
    cast: (value, context) => {
      if (context.header || context.column === CATEGORY) {
        return value;
      }
      return value === "" ? null : Number(value);
    },
  });
}

export function prepareBoston(
  csvFile: string,
  testSize: number,
  seed: number,
): TrainTestSplit<Record<string, number>, number> {
  const rows = loadBoston(csvFile);
  const categories = dummyCategories(rows.map((row) => row[CATEGORY]));
  // CRIME 以外の列は数値か欠損（null）なので、ダミー変数化した後の行はすべて数値の列になる
  const encoded = encodeDummies(rows, CATEGORY, categories) as Record<
    string,
    number | null
  >[];
  const x = encoded.map(({ [TARGET]: _price, ...features }) => features);
  const t = encoded.map((row) => Number(row[TARGET]));
  const split = splitTrainTest(x, t, testSize, seed);
  const means = columnMeans(split.xTrain, Object.keys(x[0] ?? {}));
  return {
    ...split,
    xTrain: fillMissing(split.xTrain, means),
    xTest: fillMissing(split.xTest, means),
  };
}

function toRows(
  rows: readonly Record<string, number>[],
  columns: readonly string[],
): number[][] {
  return rows.map((row) => columns.map((column) => row[column] as number));
}

export function scoreFeatureSet(
  split: TrainTestSplit<Record<string, number>, number>,
  columns: readonly string[],
  terms: readonly string[],
): [number, number] {
  const train = polynomialFeatures(split.xTrain, columns);
  const test = polynomialFeatures(split.xTest, columns);
  const standardizer = Standardizer.fit(train, terms);
  const xTrain = toRows(standardizer.transform(train), terms);
  const xTest = toRows(standardizer.transform(test), terms);
  const model = fitLinearRegression(xTrain, split.tTrain);
  return [
    rSquared(split.tTrain, model.predict(xTrain)),
    rSquared(split.tTest, model.predict(xTest)),
  ];
}
