import { type ServerType, serve } from "@hono/node-server";
import { dataDir } from "../dataset.ts";
import {
  FileModelStore,
  SALES_MODEL,
  SURVIVAL_MODEL,
} from "./file-model-store.ts";
import { createApp } from "./prediction-api.ts";
import { PredictionService } from "./prediction-service.ts";
import { trainAndSaveModels } from "./training.ts";

/** 学習済みモデルの保存先（apps/node/model/ は .gitignore の対象） */
export const MODEL_DIR = "model";
export const HOSTNAME = "127.0.0.1";
export const PORT = 8015;

export function trainAndReport(
  modelDir: string,
  print: (line: string) => void = console.log,
): void {
  trainAndSaveModels(dataDir(), new FileModelStore(modelDir));
  print(
    `学習済みモデルを保存しました: ${SALES_MODEL}.json, ${SURVIVAL_MODEL}.json`,
  );
  print(`API を起動します: http://${HOSTNAME}:${PORT}`);
}

export function startServer(modelDir: string, port: number): ServerType {
  const app = createApp(new PredictionService(new FileModelStore(modelDir)));
  return serve({ fetch: app.fetch, hostname: HOSTNAME, port });
}

export function main(print: (line: string) => void = console.log): void {
  trainAndReport(MODEL_DIR, print);
  startServer(MODEL_DIR, PORT);
}

// node src/chapter15/main.ts で直接実行したときだけ main を呼ぶ
if (import.meta.filename === process.argv[1]) {
  main();
}
