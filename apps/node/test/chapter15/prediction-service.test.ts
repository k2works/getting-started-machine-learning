import { describe, expect, it } from "vitest";
import { ModelNotFoundError } from "../../src/chapter15/domain.ts";
import { PredictionService } from "../../src/chapter15/prediction-service.ts";
import { MOVIE, PASSENGER, emptyModelStore, stubModelStore } from "./stubs.ts";

describe("predictSales", () => {
  it("映画の特徴量から興行収入を予測する", () => {
    const service = new PredictionService(stubModelStore);

    const prediction = service.predictSales(MOVIE);

    expect(prediction).toEqual({ ok: true, value: { sales: 1200 } });
  });

  it("モデルが無ければ ModelNotFoundError の失敗を返す", () => {
    const service = new PredictionService(emptyModelStore);

    const prediction = service.predictSales(MOVIE);

    expect(prediction.ok).toBe(false);
    if (!prediction.ok) {
      expect(prediction.error).toBeInstanceOf(ModelNotFoundError);
      expect(prediction.error.message).toBe(
        "学習済みモデル cinema が見つかりません",
      );
    }
  });
});

describe("predictSurvival", () => {
  it("生存と判定されれば生存と予測する", () => {
    const service = new PredictionService(stubModelStore);

    expect(service.predictSurvival(PASSENGER)).toEqual({
      ok: true,
      value: { survived: true },
    });
  });

  it("死亡と判定されれば死亡と予測する", () => {
    const service = new PredictionService(stubModelStore);

    const prediction = service.predictSurvival({
      ...PASSENGER,
      pclass: 3,
      sex: "male",
      age: 30,
      fare: 8,
      embarked: "S",
    });

    expect(prediction).toEqual({ ok: true, value: { survived: false } });
  });
});

describe("health", () => {
  it("モデルを読み込めればそれぞれ true を返す", () => {
    const service = new PredictionService(stubModelStore);

    expect(service.health()).toEqual({ cinema: true, survived: true });
  });

  it("モデルが無ければそれぞれ false を返す", () => {
    const service = new PredictionService(emptyModelStore);

    expect(service.health()).toEqual({ cinema: false, survived: false });
  });
});
