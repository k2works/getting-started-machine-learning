import { DecisionTreeClassifier } from "ml-cart";
import { describe, expect, it } from "vitest";
import { DecisionTree } from "../../src/chapter03/decision-tree.ts";
import { trainMlCart } from "../../src/chapter03/ml-cart-adapter.ts";

describe("trainMlCart", () => {
  it("文字列の正解ラベルで学習し、文字列のラベルで予測する", () => {
    const x = [0.1, 0.2, 0.3, 0.5, 0.6, 0.9].map((value) => ({
      花弁幅: value,
    }));
    const t = [
      "setosa",
      "setosa",
      "setosa",
      "versicolor",
      "versicolor",
      "virginica",
    ];

    const predict = trainMlCart(x, t, {});

    expect(
      predict([{ 花弁幅: 0.2 }, { 花弁幅: 0.55 }, { 花弁幅: 0.95 }]),
    ).toEqual(["setosa", "versicolor", "virginica"]);
  });

  it("境界ちょうどの値が無ければ自作の決定木と同じ予測をする", () => {
    const x = [0.1, 0.2, 0.3, 0.5, 0.6, 0.9].map((value) => ({
      花弁幅: value,
    }));
    const t = [
      "setosa",
      "setosa",
      "setosa",
      "versicolor",
      "versicolor",
      "virginica",
    ];
    const newX = [0.2, 0.45, 0.55, 0.8, 0.95].map((value) => ({
      花弁幅: value,
    }));

    const predict = trainMlCart(x, t, {});

    expect(predict(newX)).toEqual(new DecisionTree().fit(x, t).predict(newX));
  });

  it("境界ちょうどの値を自作の決定木は左に、ml-cart は右に進める", () => {
    const x = [0.1, 0.2, 0.3, 0.5, 0.6, 0.9].map((value) => ({
      花弁幅: value,
    }));
    const t = [
      "setosa",
      "setosa",
      "setosa",
      "versicolor",
      "versicolor",
      "virginica",
    ];
    const onBoundary = [0.4, 0.75].map((value) => ({ 花弁幅: value }));

    const predict = trainMlCart(x, t, {});

    expect(new DecisionTree().fit(x, t).predict(onBoundary)).toEqual([
      "setosa",
      "versicolor",
    ]);
    expect(predict(onBoundary)).toEqual(["versicolor", "virginica"]);
  });

  it("葉の多数決が同数のとき、自作は葉の中で先に現れたラベル、ml-cart は訓練データ全体で先に現れたラベルを選ぶ", () => {
    const x = [0.9, 0.1, 0.1].map((value) => ({ 花弁幅: value }));
    const t = ["b", "a", "b"];

    const predict = trainMlCart(x, t, {});

    expect(new DecisionTree().fit(x, t).predict([{ 花弁幅: 0.1 }])).toEqual([
      "a",
    ]);
    expect(predict([{ 花弁幅: 0.1 }])).toEqual(["b"]);
  });
});

describe("ml-cart の既定の設定", () => {
  it("利得が 0.01 以下の分割はしない", () => {
    const x = Array.from({ length: 100 }, (_, i) => [i]);
    const y = x.map(([i]) => (i === 50 ? 1 : 0));

    const classifier = new DecisionTreeClassifier({ minNumSamples: 1 });
    classifier.train(x, y);

    expect(classifier.predict([[50]])).toEqual([0]);
  });

  it("アダプターは利得の下限を 0 にして、利得が小さい分割もする", () => {
    const x = Array.from({ length: 100 }, (_, i) => ({ f: i }));
    const t = x.map(({ f }) => (f === 50 ? "b" : "a"));

    const predict = trainMlCart(x, t, {});

    expect(predict([{ f: 50 }])).toEqual(["b"]);
  });
});
