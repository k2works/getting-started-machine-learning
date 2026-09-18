import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { beforeEach, describe, expect, it } from "vitest";
import { fitPipeline } from "../../src/chapter08/pipeline.ts";
import type { Passenger as SurvivedPassenger } from "../../src/chapter08/survived-data.ts";
import {
  ModelNotFoundError,
  type Passenger,
  type Result,
} from "../../src/chapter15/domain.ts";
import { FileModelStore } from "../../src/chapter15/file-model-store.ts";

function passenger(sex: string): Passenger {
  return {
    pclass: 2,
    sex,
    age: null,
    sibSp: 0,
    parch: 0,
    fare: 20,
    embarked: null,
  };
}

/** 架空の 8 人の乗客。女性は生存、男性は死亡 */
function fictionalPassengers(): { x: SurvivedPassenger[]; t: number[] } {
  const rows: [number, string, number | null, number, string | null][] = [
    [1, "female", 25, 60, "C"],
    [2, "female", 35, 30, "S"],
    [3, "female", 18, 10, "Q"],
    [3, "female", null, 9, "S"],
    [1, "male", 40, 55, "C"],
    [2, "male", 28, 15, "S"],
    [3, "male", 22, 8, null],
    [3, "male", null, 7, "S"],
  ];
  return {
    x: rows.map(([Pclass, Sex, Age, Fare, Embarked]) => ({
      Pclass,
      Sex,
      Age,
      SibSp: 0,
      Parch: 0,
      Fare,
      Embarked,
    })),
    t: [1, 1, 1, 1, 0, 0, 0, 0],
  };
}

function valueOf<T>(result: Result<T>): T {
  if (!result.ok) {
    throw result.error;
  }
  return result.value;
}

let directory: string;

beforeEach(() => {
  directory = mkdtempSync(join(tmpdir(), "model-"));
});

describe("興行収入のモデル", () => {
  it("保存した線形回帰モデルを読み込んで興行収入を予測する", () => {
    const store = new FileModelStore(directory);
    store.saveSalesModel({
      intercept: 100,
      coefficients: { SNS1: 1, SNS2: 2, actor: 0.5, original: 10 },
    });

    const model = valueOf(store.loadSalesModel());

    expect(
      model.predictSales({ sns1: 10, sns2: 20, actor: 100, original: 1 }),
    ).toBeCloseTo(210, 9);
  });

  it("モデルファイルが無ければ ModelNotFoundError の失敗を返す", () => {
    const store = new FileModelStore(directory);

    const result = store.loadSalesModel();

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.error).toBeInstanceOf(ModelNotFoundError);
      expect(result.error.message).not.toContain(directory);
    }
  });

  it("モデルファイルの形が違えば例外を投げずに失敗を返す", () => {
    writeFileSync(join(directory, "cinema.json"), '{"intercept": "高い"}');
    const store = new FileModelStore(directory);

    const result = store.loadSalesModel();

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.error).not.toBeInstanceOf(ModelNotFoundError);
    }
  });
});

describe("生存のモデル", () => {
  it("保存したパイプラインを読み込んで生存を判定する", () => {
    const store = new FileModelStore(directory);
    const { x, t } = fictionalPassengers();
    store.saveSurvivalModel(
      fitPipeline(x, t, { maxDepth: 2, classWeight: "none" }),
    );

    const model = valueOf(store.loadSurvivalModel());

    expect(model.survives(passenger("female"))).toBe(true);
    expect(model.survives(passenger("male"))).toBe(false);
  });

  it("モデルファイルが無ければ ModelNotFoundError の失敗を返す", () => {
    const store = new FileModelStore(directory);

    const result = store.loadSurvivalModel();

    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.error).toBeInstanceOf(ModelNotFoundError);
      expect(result.error.message).not.toContain(directory);
    }
  });
});
