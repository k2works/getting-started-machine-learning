import { describe, expect, it } from "vitest";
import {
  accuracy,
  classificationMetric,
  confusionMatrix,
  f1Score,
  meanAbsoluteError,
  meanSquaredError,
  precision,
  recall,
  rootMeanSquaredError,
} from "../../src/chapter11/metrics.ts";

describe("confusionMatrix", () => {
  it("正例と負例の予測の当たり外れを数える", () => {
    const actual = [1, 1, 1, 0, 0];
    const predicted = [1, 1, 0, 1, 0];

    expect(confusionMatrix(actual, predicted, 1)).toEqual({
      tp: 2,
      fp: 1,
      fn: 1,
      tn: 1,
    });
  });

  it("どちらのラベルを正例とするかで数え方が変わる", () => {
    const actual = [1, 1, 1, 0, 0, 0];
    const predicted = [1, 0, 0, 0, 0, 1];

    expect(confusionMatrix(actual, predicted, 0)).toEqual({
      tp: 2,
      fp: 2,
      fn: 1,
      tn: 1,
    });
  });

  it("正解と予測の件数が違えばエラーになる", () => {
    expect(() => confusionMatrix([1, 0, 1], [1, 0], 1)).toThrow(
      "正解と予測の件数が違います",
    );
  });
});

describe("適合率・再現率・F 値", () => {
  const cm = { tp: 3, fp: 1, fn: 2, tn: 4 };

  it("適合率は正例と予測したうち本当に正例だった割合", () => {
    expect(precision(cm)).toBeCloseTo(0.75, 12);
  });

  it("再現率は本当の正例のうち正例と予測できた割合", () => {
    expect(recall(cm)).toBeCloseTo(0.6, 12);
  });

  it("F 値は適合率と再現率の調和平均", () => {
    expect(f1Score(cm)).toBeCloseTo((2 * 0.75 * 0.6) / (0.75 + 0.6), 12);
  });

  it("正例を 1 件も当てられなければ適合率と再現率と F 値は 0", () => {
    const missed = { tp: 0, fp: 0, fn: 3, tn: 5 };

    expect([precision(missed), recall(missed), f1Score(missed)]).toEqual([
      0, 0, 0,
    ]);
  });
});

describe("回帰の評価指標", () => {
  it("誤差の 2 乗の平均と平方根と絶対値の平均を求める", () => {
    const actual = [3, 5, 8];
    const predicted = [2, 5, 10];

    expect(meanSquaredError(actual, predicted)).toBeCloseTo(5 / 3, 12);
    expect(rootMeanSquaredError(actual, predicted)).toBeCloseTo(
      Math.sqrt(5 / 3),
      12,
    );
    expect(meanAbsoluteError(actual, predicted)).toBeCloseTo(1, 12);
  });

  it("大きく外れた予測があると RMSE は MAE より大きく増える", () => {
    const actual = [3, 5, 8, 10];
    const predicted = [2, 5, 10, 30];

    expect(rootMeanSquaredError(actual, predicted)).toBeCloseTo(
      Math.sqrt(101.25),
      12,
    );
    expect(meanAbsoluteError(actual, predicted)).toBeCloseTo(5.75, 12);
  });

  it("回帰の評価指標も正解と予測の件数が違えばエラーになる", () => {
    expect(() => meanSquaredError([1, 2], [1])).toThrow(
      "正解と予測の件数が違います",
    );
  });
});

describe("分類の評価関数", () => {
  it("正解率は正解と予測が一致した割合", () => {
    expect(accuracy([1, 0, 1, 0], [1, 1, 1, 0])).toBeCloseTo(0.75, 12);
  });

  it("混同行列から求める指標を正解と予測から求める評価関数に変える", () => {
    const actual = [1, 1, 1, 0, 0];
    const predicted = [1, 0, 0, 1, 0];

    const precisionMetric = classificationMetric(precision, 1);
    const recallMetric = classificationMetric(recall, 1);

    expect(precisionMetric(actual, predicted)).toBeCloseTo(0.5, 12);
    expect(recallMetric(actual, predicted)).toBeCloseTo(1 / 3, 12);
  });

  it("正解率も正解と予測の件数が違えばエラーになる", () => {
    expect(() => accuracy([1, 0, 1], [1, 0])).toThrow(
      "正解と予測の件数が違います",
    );
  });
});
