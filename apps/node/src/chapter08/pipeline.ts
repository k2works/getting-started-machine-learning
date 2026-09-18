import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";
import { z } from "zod";
import {
  type DecisionTreeOptions,
  type WeightedTree,
  fitDecisionTree,
  predictDecisionTree,
} from "./decision-tree-classifier.ts";
import type { Passenger } from "./survived-data.ts";
import {
  type DummyEncoder,
  type GroupMedianImputer,
  type MostFrequentImputer,
  encodeDummies,
  fitDummyEncoder,
  fitGroupMedianImputer,
  fitMostFrequentImputer,
  imputeGroupMedian,
  imputeMostFrequent,
} from "./transformers.ts";

/** 学習済みの前処理とモデル。予測するときは、ここに覚えた値だけを使う */
export interface FittedPipeline {
  age: GroupMedianImputer<"Age", "Pclass" | "Sex">;
  embarked: MostFrequentImputer<"Embarked">;
  dummies: DummyEncoder<"Sex" | "Embarked">;
  tree: WeightedTree;
}

/** 前処理を順に学習・変換してから、決定木を学習する */
export function fitPipeline(
  x: readonly Passenger[],
  t: readonly number[],
  options: DecisionTreeOptions,
): FittedPipeline {
  const age = fitGroupMedianImputer(x, "Age", ["Pclass", "Sex"]);
  const ageFilled = imputeGroupMedian(x, age);
  const embarked = fitMostFrequentImputer(ageFilled, "Embarked");
  const complete = imputeMostFrequent(ageFilled, embarked);
  const dummies = fitDummyEncoder(complete, ["Sex", "Embarked"]);
  const tree = fitDecisionTree(encodeDummies(complete, dummies), t, options);
  return { age, embarked, dummies, tree };
}

/** 学習済みの前処理で、乗客のデータを決定木に渡せる数値の列にする */
export function transform(
  pipeline: FittedPipeline,
  x: readonly Passenger[],
): Record<string, number>[] {
  const ageFilled = imputeGroupMedian(x, pipeline.age);
  const complete = imputeMostFrequent(ageFilled, pipeline.embarked);
  return encodeDummies(complete, pipeline.dummies);
}

export function predict(
  pipeline: FittedPipeline,
  x: readonly Passenger[],
): number[] {
  return predictDecisionTree(pipeline.tree, transform(pipeline, x));
}

export function saveModel(pipeline: FittedPipeline, modelFile: string): void {
  mkdirSync(dirname(modelFile), { recursive: true });
  writeFileSync(modelFile, JSON.stringify(pipeline));
}

const treeSchema: z.ZodType<WeightedTree> = z.lazy(() =>
  z.discriminatedUnion("kind", [
    z.object({ kind: z.literal("leaf"), label: z.number() }),
    z.object({
      kind: z.literal("node"),
      feature: z.string(),
      threshold: z.number(),
      left: treeSchema,
      right: treeSchema,
    }),
  ]),
);

/** 保存したパイプラインの形。読み込んだ JSON がこの形でなければ読み込みを止める */
const pipelineSchema: z.ZodType<FittedPipeline> = z.object({
  age: z.object({
    column: z.literal("Age"),
    by: z.array(z.enum(["Pclass", "Sex"])),
    medians: z.record(z.string(), z.number()),
    overallMedian: z.number(),
  }),
  embarked: z.object({
    column: z.literal("Embarked"),
    mostFrequent: z.string(),
  }),
  dummies: z.object({
    dummies: z.object({
      Sex: z.array(z.string()),
      Embarked: z.array(z.string()),
    }),
  }),
  tree: treeSchema,
});

export function loadModel(modelFile: string): FittedPipeline {
  return pipelineSchema.parse(JSON.parse(readFileSync(modelFile, "utf8")));
}

/** 乗客 1 人分のデータから、生存（1）か死亡（0）かを予測する */
export function predictPassenger(
  pipeline: FittedPipeline,
  passenger: Passenger,
): number {
  return predict(pipeline, [passenger])[0] as number;
}
