import { type Context, Hono } from "hono";
import { z } from "zod";
import { type Passenger, type Result, ModelNotFoundError } from "./domain.ts";
import type { PredictionService } from "./prediction-service.ts";

function notNegative(field: string) {
  return z
    .number({ error: `${field} は数値にしてください` })
    .nonnegative({ error: `${field} は 0 以上にしてください` });
}

function notNegativeInt(field: string) {
  return z
    .int({ error: `${field} は整数にしてください` })
    .nonnegative({ error: `${field} は 0 以上にしてください` });
}

function oneOf<
  const T extends readonly [string | number, ...(string | number)[]],
>(field: string, values: T) {
  return z.literal(values, {
    error: `${field} は ${values.join("、")} のどれかにしてください`,
  });
}

/** 映画の API の入力。JSON の項目名はドメインの Movie と同じ */
const movieRequestSchema = z.object({
  sns1: notNegative("sns1"),
  sns2: notNegative("sns2"),
  actor: notNegative("actor"),
  original: oneOf("original", [0, 1]),
});

/** 乗客の API の入力。年齢と乗船港は省略でき、欠損値の補完は第 8 章のパイプラインに任せる */
const passengerRequestSchema = z
  .object({
    pclass: oneOf("pclass", [1, 2, 3]),
    sex: oneOf("sex", ["male", "female"]),
    age: notNegative("age").nullish(),
    sib_sp: notNegativeInt("sib_sp"),
    parch: notNegativeInt("parch"),
    fare: notNegative("fare"),
    embarked: oneOf("embarked", ["C", "Q", "S"]).nullish(),
  })
  .transform((request): Passenger => ({
    pclass: request.pclass,
    sex: request.sex,
    age: request.age ?? null,
    sibSp: request.sib_sp,
    parch: request.parch,
    fare: request.fare,
    embarked: request.embarked ?? null,
  }));

/** 入力の検証結果。正しければ値を、不正なら理由の一覧を持つ */
type Validated<T> = { ok: true; value: T } | { ok: false; errors: string[] };

/** 本文を JSON として読み、スキーマで検証してドメインの型にする */
async function validate<T>(
  c: Context,
  schema: z.ZodType<T, unknown>,
): Promise<Validated<T>> {
  let body: unknown;
  try {
    body = await c.req.json();
  } catch {
    return { ok: false, errors: ["JSON の形式が正しくありません"] };
  }
  const parsed = schema.safeParse(body);
  return parsed.success
    ? { ok: true, value: parsed.data }
    : { ok: false, errors: parsed.error.issues.map((issue) => issue.message) };
}

/** 成功していれば値を返し、失敗していれば原因の例外を投げる */
function valueOrThrow<T>(result: Result<T>): T {
  if (!result.ok) {
    throw result.error;
  }
  return result.value;
}

export function createApp(service: PredictionService): Hono {
  const app = new Hono();

  app.post("/cinema/sales", async (c) => {
    const movie = await validate(c, movieRequestSchema);
    if (!movie.ok) {
      return c.json({ detail: movie.errors }, 422);
    }
    return c.json(valueOrThrow(service.predictSales(movie.value)));
  });

  app.post("/survived", async (c) => {
    const passenger = await validate(c, passengerRequestSchema);
    if (!passenger.ok) {
      return c.json({ detail: passenger.errors }, 422);
    }
    return c.json(valueOrThrow(service.predictSurvival(passenger.value)));
  });

  app.get("/health", (c) => {
    const models = service.health();
    const status = Object.values(models).every(Boolean) ? "ok" : "degraded";
    return c.json({ status, models });
  });

  // ドメインの例外を HTTP のステータスコードに変換する。内部の情報は応答に出さない
  app.onError((error, c) => {
    if (error instanceof ModelNotFoundError) {
      return c.json({ detail: error.message }, 503);
    }
    console.error(error);
    return c.json({ detail: "予測中にエラーが発生しました" }, 500);
  });

  return app;
}
