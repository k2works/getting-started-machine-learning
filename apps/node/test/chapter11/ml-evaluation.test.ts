import { ConfusionMatrix } from "ml-confusion-matrix";
import { getFolds, kFold as mlKFold } from "ml-cross-validation";
import { afterEach, describe, expect, it, vi } from "vitest";
import { createRandom } from "../../src/chapter02/random.ts";
import { trainMlCart } from "../../src/chapter03/ml-cart-adapter.ts";
import { crossValidate, kFold } from "../../src/chapter11/cross-validation.ts";
import {
  accuracy,
  confusionMatrix,
  f1Score,
  mean,
  precision,
  recall,
} from "../../src/chapter11/metrics.ts";
import { MlCartTree } from "./ml-cart-model.ts";

type Row = { feature: number };

const x: Row[] = [0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9, 1.0].map(
  (value) => ({
    feature: value,
  }),
);
const t = ["0", "0", "1", "0", "0", "1", "1", "0", "1", "1"];

describe("ml-confusion-matrix との突き合わせ", () => {
  const predicted = trainMlCart(x, t, { maxDepth: 1 })(x);
  const library = ConfusionMatrix.fromLabels(t, predicted);

  it("混同行列が ml-confusion-matrix と一致する", () => {
    const cm = confusionMatrix(t, predicted, "1");

    expect(cm).toEqual({
      tp: library.getTruePositiveCount("1"),
      fp: library.getFalsePositiveCount("1"),
      fn: library.getFalseNegativeCount("1"),
      tn: library.getTrueNegativeCount("1"),
    });
  });

  it("正解率と適合率と再現率と F 値が ml-confusion-matrix と一致する", () => {
    const cm = confusionMatrix(t, predicted, "1");

    expect(accuracy(t, predicted)).toBeCloseTo(library.getAccuracy(), 12);
    expect(precision(cm)).toBeCloseTo(
      library.getPositivePredictiveValue("1"),
      12,
    );
    expect(recall(cm)).toBeCloseTo(library.getTruePositiveRate("1"), 12);
    expect(f1Score(cm)).toBeCloseTo(library.getF1Score("1"), 12);
  });

  it("正例を 1 件も予測しなければ ml-confusion-matrix の適合率は NaN になる", () => {
    const neverPositive = ConfusionMatrix.fromLabels(
      ["1", "1", "1", "0"],
      ["0", "0", "0", "0"],
    );

    expect(neverPositive.getPositivePredictiveValue("1")).toBeNaN();
    expect(neverPositive.getTruePositiveRate("1")).toBe(0);
    expect(neverPositive.getF1Score("1")).toBe(0);
  });

  it("正解にも予測にも無いラベルを正例にすると ml-confusion-matrix は例外を投げる", () => {
    const onlyNegative = ConfusionMatrix.fromLabels<string>(
      ["0", "0"],
      ["0", "0"],
    );

    expect(() => onlyNegative.getTruePositiveCount("1")).toThrow(
      "The label does not exist",
    );
    expect(confusionMatrix(["0", "0"], ["0", "0"], "1")).toEqual({
      tp: 0,
      fp: 0,
      fn: 0,
      tn: 2,
    });
  });
});

describe("ml-cross-validation との突き合わせ", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  function libraryTestSizes(nSamples: number, nSplits: number): number[] {
    const features = Array.from({ length: nSamples }, (_, i) => i);
    return getFolds(features, nSplits).map((fold) => fold.testIndex.length);
  }

  it("余りの行を先頭ではなく最後の分割に足す", () => {
    expect(libraryTestSizes(10, 3)).toEqual([3, 3, 4]);
    expect(kFold(10, 3, 0).map((fold) => fold.test.length)).toEqual([4, 3, 3]);
    expect(libraryTestSizes(7, 2)).toEqual([3, 4]);
    expect(kFold(7, 2, 0).map((fold) => fold.test.length)).toEqual([4, 3]);
  });

  it("余りが分割数以上になると一度もテストデータにならない行が出る", () => {
    const tested = getFolds(
      Array.from({ length: 11 }, (_, i) => i),
      4,
    ).flatMap((fold) => fold.testIndex);

    expect(tested).toHaveLength(9);
    expect(kFold(11, 4, 0).flatMap((fold) => fold.test)).toHaveLength(11);
  });

  it("シードを受け取らないが Math.random を差し替えると分け方を再現できる", () => {
    const features = Array.from({ length: 10 }, (_, i) => i);

    vi.spyOn(Math, "random").mockImplementation(createRandom(0));
    const first = getFolds(features, 3);
    vi.spyOn(Math, "random").mockImplementation(createRandom(0));
    const second = getFolds(features, 3);

    expect(first).toEqual(second);
  });

  it("すべての分割の予測を 1 つの混同行列に足し合わせる", () => {
    vi.spyOn(Math, "random").mockImplementation(createRandom(0));
    const rows = x.map((_, i): [number] => [i]);
    const perFold: { actual: string[]; predicted: string[] }[] = [];

    const pooled = mlKFold(rows, t, 3, (trainRows, trainLabels, testRows) => {
      const trainX = trainRows.map(([i]) => x[i] as Row);
      const testX = testRows.map(([i]) => x[i] as Row);
      const predicted = trainMlCart(trainX, trainLabels, { maxDepth: 1 })(
        testX,
      );
      perFold.push({
        actual: testRows.map(([i]) => t[i] as string),
        predicted,
      });
      return predicted;
    });

    const summed = perFold
      .map(({ actual, predicted }) => confusionMatrix(actual, predicted, "1"))
      .reduce((sum, cm) => ({
        tp: sum.tp + cm.tp,
        fp: sum.fp + cm.fp,
        fn: sum.fn + cm.fn,
        tn: sum.tn + cm.tn,
      }));
    expect(summed).toEqual({
      tp: pooled.getTruePositiveCount("1"),
      fp: pooled.getFalsePositiveCount("1"),
      fn: pooled.getFalseNegativeCount("1"),
      tn: pooled.getTrueNegativeCount("1"),
    });

    const weighted =
      perFold.reduce(
        (sum, { actual, predicted }) =>
          sum + accuracy(actual, predicted) * actual.length,
        0,
      ) / x.length;
    expect(pooled.getAccuracy()).toBeCloseTo(weighted, 12);
  });
});

describe("ml-cart の決定木での交差検証", () => {
  it("同じ分割なら ml-confusion-matrix で採点した正解率の平均と一致する", () => {
    const rows = Array.from({ length: 20 }, (_, i) => ({
      feature: (i + 1) * 0.05,
    }));
    const labels = rows.map((_, i) =>
      (i + 1) % 3 === 0 || i + 1 > 12 ? "1" : "0",
    );
    const folds = kFold(20, 4, 0);

    const libraryScores = folds.map((fold) => {
      const trainX = fold.train.map((i) => rows[i] as Row);
      const trainT = fold.train.map((i) => labels[i] as string);
      const testX = fold.test.map((i) => rows[i] as Row);
      const testT = fold.test.map((i) => labels[i] as string);
      const predicted = trainMlCart(trainX, trainT, { maxDepth: 1 })(testX);
      return ConfusionMatrix.fromLabels(testT, predicted).getAccuracy();
    });

    const scores = [
      ...crossValidate(() => new MlCartTree(1), rows, labels, folds, accuracy),
    ];
    expect(mean(scores)).toBeCloseTo(mean(libraryScores), 12);
  });
});
