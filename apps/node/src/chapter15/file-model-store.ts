import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { z } from "zod";
import {
  type Feature,
  type LinearModel,
  predict as predictLinear,
} from "../chapter07/cinema-regression.ts";
import {
  type FittedPipeline,
  loadModel,
  predictPassenger,
  saveModel,
} from "../chapter08/pipeline.ts";
import {
  type ModelStore,
  type Result,
  type SalesModel,
  type SurvivalModel,
  ModelNotFoundError,
} from "./domain.ts";

export const SALES_MODEL = "cinema";
export const SURVIVAL_MODEL = "survived";

/** 第 7 章の線形回帰モデルを、ドメインの SalesModel の約束に合わせるアダプター */
export function linearSalesModel(model: LinearModel<Feature>): SalesModel {
  return {
    predictSales: (movie) =>
      predictLinear(model, [
        {
          SNS1: movie.sns1,
          SNS2: movie.sns2,
          actor: movie.actor,
          original: movie.original,
        },
      ])[0] as number,
  };
}

/** 第 8 章の学習済みパイプラインを、ドメインの SurvivalModel の約束に合わせるアダプター */
export function pipelineSurvivalModel(pipeline: FittedPipeline): SurvivalModel {
  return {
    survives: (passenger) =>
      predictPassenger(pipeline, {
        Pclass: passenger.pclass,
        Sex: passenger.sex,
        Age: passenger.age,
        SibSp: passenger.sibSp,
        Parch: passenger.parch,
        Fare: passenger.fare,
        Embarked: passenger.embarked,
      }) === 1,
  };
}

/** 保存した線形回帰モデルの形。読み込んだ JSON がこの形でなければ読み込みを止める */
const linearModelSchema: z.ZodType<LinearModel<Feature>> = z.object({
  intercept: z.number(),
  coefficients: z.object({
    SNS1: z.number(),
    SNS2: z.number(),
    actor: z.number(),
    original: z.number(),
  }),
});

/** 学習済みモデルをディレクトリのファイルに保存し、読み込む */
export class FileModelStore implements ModelStore {
  readonly #modelDir: string;

  constructor(modelDir: string) {
    this.#modelDir = modelDir;
  }

  saveSalesModel(model: LinearModel<Feature>): void {
    mkdirSync(this.#modelDir, { recursive: true });
    writeFileSync(this.#salesModelFile(), JSON.stringify(model));
  }

  saveSurvivalModel(pipeline: FittedPipeline): void {
    saveModel(pipeline, this.#survivalModelFile());
  }

  loadSalesModel(): Result<SalesModel> {
    return load(SALES_MODEL, this.#salesModelFile(), (file) =>
      linearSalesModel(
        linearModelSchema.parse(JSON.parse(readFileSync(file, "utf8"))),
      ),
    );
  }

  loadSurvivalModel(): Result<SurvivalModel> {
    return load(SURVIVAL_MODEL, this.#survivalModelFile(), (file) =>
      pipelineSurvivalModel(loadModel(file)),
    );
  }

  #salesModelFile(): string {
    return join(this.#modelDir, `${SALES_MODEL}.json`);
  }

  #survivalModelFile(): string {
    return join(this.#modelDir, `${SURVIVAL_MODEL}.json`);
  }
}

/** ファイルが無ければ ModelNotFoundError の失敗を、読み込みで例外が起きればその失敗を返す */
function load<T>(
  model: string,
  file: string,
  read: (file: string) => T,
): Result<T> {
  if (!existsSync(file)) {
    return { ok: false, error: new ModelNotFoundError(model) };
  }
  try {
    return { ok: true, value: read(file) };
  } catch (error) {
    return {
      ok: false,
      error: error instanceof Error ? error : new Error(String(error)),
    };
  }
}
