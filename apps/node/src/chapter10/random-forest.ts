import { createRandom, shuffle } from "../chapter02/random.ts";
import { DecisionTree, majority } from "../chapter03/decision-tree.ts";

export function majorityVote(votes: readonly (readonly string[])[]): string[] {
  const samples = votes[0] ?? [];
  return samples.map((_, sample) =>
    majority(votes.map((predictions) => predictions[sample] as string)),
  );
}

export function bootstrapSample(size: number, random: () => number): number[] {
  return Array.from({ length: size }, () => Math.floor(random() * size));
}

export function selectColumns<K extends string>(
  x: readonly Record<K, number>[],
  columns: readonly K[],
): Record<K, number>[] {
  return x.map(
    (row) =>
      Object.fromEntries(
        columns.map((column) => [column, row[column]]),
      ) as Record<K, number>,
  );
}

export interface FittedTree<K extends string> {
  columns: K[];
  rows: number[];
  model: DecisionTree<K>;
}

export interface RandomForestOptions {
  /** 決定木の数。省略すると 10 */
  nEstimators?: number;
  /** 1 本の木が使う特徴量の数。省略すると 2 */
  maxFeatures?: number;
  /** 木の深さの上限。省略すると制限しない */
  maxDepth?: number;
  /** 乱数のシード。省略すると 0 */
  seed?: number;
}

export class RandomForest<K extends string> {
  readonly nEstimators: number;
  readonly maxFeatures: number;
  readonly maxDepth: number | undefined;
  readonly seed: number;
  trees: FittedTree<K>[] = [];

  constructor(options: RandomForestOptions = {}) {
    this.nEstimators = options.nEstimators ?? 10;
    this.maxFeatures = options.maxFeatures ?? 2;
    this.maxDepth = options.maxDepth;
    this.seed = options.seed ?? 0;
  }

  fit(x: readonly Record<K, number>[], t: readonly string[]): this {
    const random = createRandom(this.seed);
    const features = Object.keys(x[0] ?? {}) as K[];
    this.trees = Array.from({ length: this.nEstimators }, () => {
      const rows = bootstrapSample(x.length, random);
      const chosen = new Set(
        shuffle(features, random).slice(0, this.maxFeatures),
      );
      const columns = features.filter((feature) => chosen.has(feature));
      const model = new DecisionTree<K>({ maxDepth: this.maxDepth }).fit(
        selectColumns(
          rows.map((row) => x[row] as Record<K, number>),
          columns,
        ),
        rows.map((row) => t[row] as string),
      );
      return { columns, rows, model };
    });
    return this;
  }

  predict(x: readonly Record<K, number>[]): string[] {
    if (this.trees.length === 0) {
      throw new Error("fit で学習してから predict を呼んでください");
    }
    return majorityVote(
      this.trees.map((tree) =>
        tree.model.predict(selectColumns(x, tree.columns)),
      ),
    );
  }
}
