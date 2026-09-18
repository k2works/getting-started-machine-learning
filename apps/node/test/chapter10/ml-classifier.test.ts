import { describe, expect, it } from "vitest";
import type { TrainTestSplit } from "../../src/chapter02/iris-preprocessing.ts";
import { type Classifier, evaluate } from "../../src/chapter10/classifier.ts";
import {
  MlClassifier,
  mlLogisticRegression,
  mlRandomForest,
} from "../../src/chapter10/ml-classifier.ts";

type Row = Record<"がく片幅" | "花弁幅", number>;

function twoSpeciesSplit(): TrainTestSplit<Row, string> {
  const sepalWidths = [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4, 0.5, 0.3];
  const petalWidths = [
    0.1, 0.12, 0.14, 0.16, 0.18, 0.8, 0.82, 0.84, 0.86, 0.88,
  ];
  return {
    xTrain: sepalWidths.map((sepalWidth, i) => ({
      がく片幅: sepalWidth,
      花弁幅: petalWidths[i] as number,
    })),
    xTest: [
      { がく片幅: 0.4, 花弁幅: 0.13 },
      { がく片幅: 0.4, 花弁幅: 0.83 },
    ],
    tTrain: [...Array(5).fill("setosa"), ...Array(5).fill("virginica")],
    tTest: ["setosa", "virginica"],
  };
}

describe("MlClassifier", () => {
  it("ml.js のロジスティック回帰とランダムフォレストも同じ関数で評価できる", () => {
    const models: Classifier<"がく片幅" | "花弁幅">[] = [
      mlLogisticRegression(),
      mlRandomForest({ nEstimators: 50, maxFeatures: 2, seed: 0 }),
    ];

    const scores = models.map((model) => evaluate(model, twoSpeciesSplit()));

    expect(scores).toEqual([
      { train: 1, test: 1 },
      { train: 1, test: 1 },
    ]);
  });

  it("学習する前に予測するとエラーになる", () => {
    expect(() => mlLogisticRegression().predict([{ 花弁幅: 0.1 }])).toThrow(
      "fit で学習してから predict を呼んでください",
    );
  });

  it("文字列のラベルを 0 始まりの整数にして学習し、予測を文字列に戻す", () => {
    const received: number[][] = [];
    const model = new MlClassifier(() => ({
      train: (_x: number[][], y: number[]) => {
        received.push(y);
      },
      predict: (x: number[][]) => x.map(() => 1),
    }));

    model.fit(
      [{ 花弁幅: 0.1 }, { 花弁幅: 0.9 }, { 花弁幅: 0.2 }],
      ["virginica", "setosa", "virginica"],
    );

    expect(received).toEqual([[1, 0, 1]]);
    expect(model.predict([{ 花弁幅: 0.5 }])).toEqual(["virginica"]);
  });

  it("切片が無いと、特徴量が正の値だけのデータを境界の左右に分けられない", () => {
    const x = [
      { 花弁幅: 0.1 },
      { 花弁幅: 0.2 },
      { 花弁幅: 0.8 },
      { 花弁幅: 0.9 },
    ];
    const t = ["setosa", "setosa", "virginica", "virginica"];
    const newX = [{ 花弁幅: 0.15 }, { 花弁幅: 0.85 }];

    const withoutIntercept = mlLogisticRegression().fit(x, t);
    const withIntercept = mlLogisticRegression({ intercept: true }).fit(x, t);

    expect(withoutIntercept.predict(newX)).not.toEqual(["setosa", "virginica"]);
    expect(withIntercept.predict(newX)).toEqual(["setosa", "virginica"]);
  });
});
