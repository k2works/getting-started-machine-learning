import { describe, expect, it } from "vitest";
import type { ModelStore } from "../../src/chapter15/domain.ts";
import { createApp } from "../../src/chapter15/prediction-api.ts";
import { PredictionService } from "../../src/chapter15/prediction-service.ts";
import { emptyModelStore, stubModelStore } from "./stubs.ts";

const MOVIE_JSON = { sns1: 200, sns2: 500, actor: 3000, original: 1 };
const PASSENGER_JSON = {
  pclass: 1,
  sex: "female",
  age: null,
  sib_sp: 0,
  parch: 0,
  fare: 50,
  embarked: "C",
};

/** スタブの置き場を使うサービスで API を組み立てる */
function appWith(store: ModelStore) {
  return createApp(new PredictionService(store));
}

/** スタブの置き場を使うサービスで API を組み立て、JSON を POST する */
function postJson(store: ModelStore, path: string, body: unknown) {
  return appWith(store).request(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

describe("POST /cinema/sales", () => {
  it("映画の特徴量を送ると予測した興行収入を返す", async () => {
    const response = await postJson(
      stubModelStore,
      "/cinema/sales",
      MOVIE_JSON,
    );

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ sales: 1200 });
  });

  it.each([
    ["sns1", -1],
    ["actor", "多い"],
    ["original", 2],
  ])("特徴量が不正なら 422 を返す（%s=%s）", async (field, value) => {
    const response = await postJson(stubModelStore, "/cinema/sales", {
      ...MOVIE_JSON,
      [field]: value,
    });

    expect(response.status).toBe(422);
  });

  it("JSON として読めなければ 422 を返す", async () => {
    const response = await appWith(stubModelStore).request("/cinema/sales", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: "{",
    });

    expect(response.status).toBe(422);
    expect(await response.json()).toEqual({
      detail: ["JSON の形式が正しくありません"],
    });
  });

  it("モデルが無ければ 503 を返す", async () => {
    const response = await postJson(
      emptyModelStore,
      "/cinema/sales",
      MOVIE_JSON,
    );

    expect(response.status).toBe(503);
    expect(await response.json()).toEqual({
      detail: "学習済みモデル cinema が見つかりません",
    });
  });
});

describe("POST /survived", () => {
  it("乗客の特徴量を送ると生存の予測を返す", async () => {
    const response = await postJson(
      stubModelStore,
      "/survived",
      PASSENGER_JSON,
    );

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ survived: true });
  });

  it("年齢と乗船港は省略できる", async () => {
    const { age: _age, embarked: _embarked, ...required } = PASSENGER_JSON;

    const response = await postJson(stubModelStore, "/survived", {
      ...required,
      sex: "male",
    });

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({ survived: false });
  });

  it.each([
    ["pclass", 4],
    ["sex", "unknown"],
    ["fare", -1],
    ["embarked", "X"],
  ])("特徴量が不正なら 422 を返す（%s=%s）", async (field, value) => {
    const response = await postJson(stubModelStore, "/survived", {
      ...PASSENGER_JSON,
      [field]: value,
    });

    expect(response.status).toBe(422);
  });

  it("不正な理由をまとめて返す", async () => {
    const response = await postJson(stubModelStore, "/survived", {
      ...PASSENGER_JSON,
      pclass: 4,
      fare: -1,
    });

    expect(await response.json()).toEqual({
      detail: [
        "pclass は 1、2、3 のどれかにしてください",
        "fare は 0 以上にしてください",
      ],
    });
  });

  it("モデルが無ければ 503 を返す", async () => {
    const response = await postJson(
      emptyModelStore,
      "/survived",
      PASSENGER_JSON,
    );

    expect(response.status).toBe(503);
  });
});

describe("GET /health", () => {
  it("すべてのモデルを読み込めれば ok を返す", async () => {
    const response = await appWith(stubModelStore).request("/health");

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({
      status: "ok",
      models: { cinema: true, survived: true },
    });
  });

  it("読み込めないモデルがあれば degraded を返す", async () => {
    const response = await appWith(emptyModelStore).request("/health");

    expect(response.status).toBe(200);
    expect(await response.json()).toEqual({
      status: "degraded",
      models: { cinema: false, survived: false },
    });
  });
});
