import { existsSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import type { Hono } from "hono";
import { beforeAll, describe, expect, it } from "vitest";
import { FileModelStore } from "../../src/chapter15/file-model-store.ts";
import { trainAndReport } from "../../src/chapter15/main.ts";
import { createApp } from "../../src/chapter15/prediction-api.ts";
import { PredictionService } from "../../src/chapter15/prediction-service.ts";
import { trainAndSaveModels } from "../../src/chapter15/training.ts";
import { dataDir } from "../../src/dataset.ts";

const hasTrainingData =
  existsSync(join(dataDir(), "cinema.csv")) &&
  existsSync(join(dataDir(), "Survived.csv"));

function postJson(app: Hono, path: string, body: unknown) {
  return app.request(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
}

describe.skipIf(!hasTrainingData)("実データで学習したモデル", () => {
  let app: Hono;

  // 学習は時間がかかるので、describe の中で 1 回だけ行う
  beforeAll(() => {
    const store = new FileModelStore(mkdtempSync(join(tmpdir(), "model-")));
    trainAndSaveModels(dataDir(), store);
    app = createApp(new PredictionService(store));
  });

  it("学習したモデルを保存するとヘルスチェックが ok になる", async () => {
    const response = await app.request("/health");

    expect(await response.json()).toEqual({
      status: "ok",
      models: { cinema: true, survived: true },
    });
  });

  it("学習した線形回帰モデルで興行収入を予測する", async () => {
    const response = await postJson(app, "/cinema/sales", {
      sns1: 200,
      sns2: 500,
      actor: 3000,
      original: 1,
    });

    expect(response.status).toBe(200);
    const { sales } = (await response.json()) as { sales: number };
    expect(sales).toBeCloseTo(7853.14, 2);
  });

  it("学習したパイプラインで 1 等客室の女性は生存と予測する", async () => {
    const response = await postJson(app, "/survived", {
      pclass: 1,
      sex: "female",
      sib_sp: 0,
      parch: 0,
      fare: 50,
    });

    expect(await response.json()).toEqual({ survived: true });
  });

  it("学習したパイプラインで 3 等客室の男性は死亡と予測する", async () => {
    const response = await postJson(app, "/survived", {
      pclass: 3,
      sex: "male",
      sib_sp: 0,
      parch: 0,
      fare: 8,
      embarked: "S",
    });

    expect(await response.json()).toEqual({ survived: false });
  });
});

describe.skipIf(!hasTrainingData)("trainAndReport", () => {
  it("学習するとモデルを保存して起動する URL を表示する", () => {
    const modelDir = mkdtempSync(join(tmpdir(), "model-"));
    const lines: string[] = [];

    trainAndReport(modelDir, (line) => lines.push(line));

    expect(lines).toEqual([
      "学習済みモデルを保存しました: cinema.json, survived.json",
      "API を起動します: http://127.0.0.1:8015",
    ]);
    expect(existsSync(join(modelDir, "cinema.json"))).toBe(true);
    expect(existsSync(join(modelDir, "survived.json"))).toBe(true);
  });
});
