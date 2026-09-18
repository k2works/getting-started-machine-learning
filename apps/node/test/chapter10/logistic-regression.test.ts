import { describe, expect, it } from "vitest";
import {
  LogisticRegression,
  softmax,
} from "../../src/chapter10/logistic-regression.ts";

describe("softmax", () => {
  it("値がすべて同じなら確率は均等になる", () => {
    expect(softmax([0, 0, 0, 0])).toEqual([0.25, 0.25, 0.25, 0.25]);
  });

  it("値の差が指数の比になる", () => {
    const [first, second] = softmax([0, Math.log(2)]);

    expect(first).toBeCloseTo(1 / 3, 12);
    expect(second).toBeCloseTo(2 / 3, 12);
  });

  it("大きな値でもあふれずに確率を求める", () => {
    expect(softmax([1000, 1000])).toEqual([0.5, 0.5]);
  });
});

describe("LogisticRegression", () => {
  it("1 種類のラベルだけを学習するとそのラベルを予測する", () => {
    const x = [{ 花弁幅: 0.1 }, { 花弁幅: 0.2 }];
    const t = ["setosa", "setosa"];

    const model = new LogisticRegression().fit(x, t);

    expect(model.predict([{ 花弁幅: 0.15 }, { 花弁幅: 0.9 }])).toEqual([
      "setosa",
      "setosa",
    ]);
  });

  it("2 種類のラベルを境界の左右で予測する", () => {
    const x = [
      { 花弁幅: 0.1 },
      { 花弁幅: 0.2 },
      { 花弁幅: 0.8 },
      { 花弁幅: 0.9 },
    ];
    const t = ["setosa", "setosa", "virginica", "virginica"];

    const model = new LogisticRegression().fit(x, t);

    expect(model.predict([{ 花弁幅: 0.15 }, { 花弁幅: 0.85 }])).toEqual([
      "setosa",
      "virginica",
    ]);
  });

  it("3 種類のラベルを 2 つの特徴量から予測する", () => {
    const x = [
      { 花弁長さ: 0.1, 花弁幅: 0.1 },
      { 花弁長さ: 0.2, 花弁幅: 0.2 },
      { 花弁長さ: 0.5, 花弁幅: 0.1 },
      { 花弁長さ: 0.6, 花弁幅: 0.2 },
      { 花弁長さ: 0.5, 花弁幅: 0.8 },
      { 花弁長さ: 0.6, 花弁幅: 0.9 },
    ];
    const t = [
      "setosa",
      "setosa",
      "versicolor",
      "versicolor",
      "virginica",
      "virginica",
    ];

    const model = new LogisticRegression().fit(x, t);

    expect(model.predict(x)).toEqual(t);
  });

  it("学習を繰り返すと損失が小さくなる", () => {
    const x = [
      { 花弁幅: 0.1 },
      { 花弁幅: 0.2 },
      { 花弁幅: 0.8 },
      { 花弁幅: 0.9 },
    ];
    const t = ["setosa", "setosa", "virginica", "virginica"];

    const model = new LogisticRegression({ epochs: 100 }).fit(x, t);

    expect(model.losses).toHaveLength(100);
    expect(model.losses.at(-1)).toBeLessThan(model.losses[0] as number);
  });

  it("学習する前に予測するとエラーになる", () => {
    expect(() => new LogisticRegression().predict([{ 花弁幅: 0.1 }])).toThrow(
      "fit で学習してから predict を呼んでください",
    );
  });
});
