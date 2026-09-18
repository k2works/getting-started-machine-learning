import { describe, expect, it } from "vitest";
import {
  type Model,
  crossValidate,
  kFold,
} from "../../src/chapter11/cross-validation.ts";
import {
  meanAbsoluteError,
  meanSquaredError,
} from "../../src/chapter11/metrics.ts";

describe("kFold", () => {
  it("データを k 個のテストデータにほぼ均等に分ける", () => {
    const folds = kFold(10, 3, 0);

    expect(folds.map((fold) => fold.test.length)).toEqual([4, 3, 3]);
  });

  it("件数と分割数が変わってもほぼ均等に分ける", () => {
    const folds = kFold(7, 2, 0);

    expect(folds.map((fold) => fold.test.length)).toEqual([4, 3]);
  });

  it("どの行もちょうど一度だけテストデータになる", () => {
    const folds = kFold(10, 3, 0);

    expect(
      folds.flatMap((fold) => fold.test).toSorted((a, b) => a - b),
    ).toEqual([0, 1, 2, 3, 4, 5, 6, 7, 8, 9]);
  });

  it("各分割の訓練データはテストデータ以外のすべての行", () => {
    const folds = kFold(10, 3, 0);

    for (const fold of folds) {
      expect(fold.train.filter((i) => fold.test.includes(i))).toEqual([]);
      expect(new Set([...fold.train, ...fold.test]).size).toBe(10);
    }
  });

  it("同じシードなら同じ分け方になる", () => {
    const first = kFold(10, 3, 42);
    const second = kFold(10, 3, 42);

    expect(first.map((fold) => fold.test)).toEqual(
      second.map((fold) => fold.test),
    );
  });

  it("シードが違えば違う分け方になる", () => {
    const first = kFold(10, 3, 0);
    const second = kFold(10, 3, 1);

    expect(first.map((fold) => fold.test)).not.toEqual(
      second.map((fold) => fold.test),
    );
  });
});

type Row = { feature: number };

/** 訓練データの正解の平均値を常に予測するテスト用のモデル */
class MeanModel implements Model<Row, number> {
  private mean = 0;

  fit(_x: readonly Row[], t: readonly number[]): void {
    this.mean = t.reduce((sum, value) => sum + value, 0) / t.length;
  }

  predict(x: readonly Row[]): number[] {
    return x.map(() => this.mean);
  }
}

describe("crossValidate", () => {
  const x = [
    { feature: 10 },
    { feature: 20 },
    { feature: 30 },
    { feature: 40 },
  ];
  const t = [1, 2, 3, 4];
  const folds = [
    { train: [0, 1], test: [2, 3] },
    { train: [2, 3], test: [0, 1] },
  ];

  it("分割ごとに訓練データで学習してテストデータを評価する", () => {
    const scores = crossValidate(
      () => new MeanModel(),
      x,
      t,
      folds,
      meanAbsoluteError,
    );

    expect([...scores]).toEqual([2, 2]);
  });

  it("評価関数を差し替えると別の指標で評価する", () => {
    const scores = crossValidate(
      () => new MeanModel(),
      x,
      t,
      folds,
      meanSquaredError,
    );

    expect([...scores]).toEqual([4.25, 4.25]);
  });

  it("最初の分割のスコアだけを取り出すなら学習は 1 回で済む", () => {
    let created = 0;
    const makeModel = () => {
      created++;
      return new MeanModel();
    };

    const [first] = crossValidate(makeModel, x, t, folds, meanAbsoluteError);

    expect(first).toBe(2);
    expect(created).toBe(1);
  });

  it("ジェネレーターは 1 度しか取り出せず 2 度目は空になる", () => {
    let created = 0;
    const makeModel = () => {
      created++;
      return new MeanModel();
    };
    const scores = crossValidate(makeModel, x, t, folds, meanAbsoluteError);

    expect([...scores]).toEqual([2, 2]);
    expect([...scores]).toEqual([]);
    expect(created).toBe(2);
  });
});
