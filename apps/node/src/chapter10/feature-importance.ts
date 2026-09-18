import { type Tree, gini } from "../chapter03/decision-tree.ts";
import { type RandomForest, selectColumns } from "./random-forest.ts";

interface Decrease<K extends string> {
  feature: K;
  amount: number;
}

function impurityDecreases<K extends string>(
  tree: Tree<K>,
  x: readonly Record<K, number>[],
  t: readonly string[],
): Decrease<K>[] {
  switch (tree.kind) {
    case "leaf":
      return [];
    case "node": {
      const { feature, threshold, impurity } = tree.split;
      const goesLeft = x.map((row) => row[feature] <= threshold);
      const left = goesLeft.flatMap((isLeft, i) => (isLeft ? [i] : []));
      const right = goesLeft.flatMap((isLeft, i) => (isLeft ? [] : [i]));
      return [
        { feature, amount: t.length * (gini(t) - impurity) },
        ...impurityDecreases(tree.left, pick(x, left), pick(t, left)),
        ...impurityDecreases(tree.right, pick(x, right), pick(t, right)),
      ];
    }
  }
}

function pick<T>(items: readonly T[], positions: readonly number[]): T[] {
  return positions.map((i) => items[i] as T);
}

export function treeImportances<K extends string>(
  tree: Tree<K>,
  x: readonly Record<K, number>[],
  t: readonly string[],
): Record<K, number> {
  const features = Object.keys(x[0] ?? {}) as K[];
  const decreases = impurityDecreases(tree, x, t);
  return normalize(
    features,
    features.map((feature) =>
      decreases
        .filter((decrease) => decrease.feature === feature)
        .reduce((sum, decrease) => sum + decrease.amount, 0),
    ),
  );
}

/** 特徴量ごとの値を、合計が 1 になるように割合にする。合計が 0 ならそのまま */
function normalize<K extends string>(
  features: readonly K[],
  totals: readonly number[],
): Record<K, number> {
  const total = totals.reduce((sum, value) => sum + value, 0);
  return Object.fromEntries(
    features.map((feature, i) => {
      const value = totals[i] as number;
      return [feature, total === 0 ? value : value / total];
    }),
  ) as Record<K, number>;
}

export function forestImportances<K extends string>(
  forest: RandomForest<K>,
  x: readonly Record<K, number>[],
  t: readonly string[],
): Record<K, number> {
  const features = Object.keys(x[0] ?? {}) as K[];
  const perTree = forest.trees.map(({ columns, rows, model }) => {
    if (model.tree === null) {
      throw new Error("fit で学習してから重要度を求めてください");
    }
    return treeImportances(
      model.tree,
      selectColumns(pick(x, rows), columns),
      pick(t, rows),
    );
  });
  return normalize(
    features,
    features.map(
      (feature) =>
        perTree.reduce(
          (sum, importances) => sum + (importances[feature] ?? 0),
          0,
        ) / forest.trees.length,
    ),
  );
}
