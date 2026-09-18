function countLabels(labels: readonly string[]): Map<string, number> {
  const counts = new Map<string, number>();
  for (const label of labels) {
    counts.set(label, (counts.get(label) ?? 0) + 1);
  }
  return counts;
}

export function gini(labels: readonly string[]): number {
  const total = labels.length;
  let sumOfSquares = 0;
  for (const count of countLabels(labels).values()) {
    sumOfSquares += (count / total) ** 2;
  }
  return 1 - sumOfSquares;
}

export interface Split<K extends string> {
  feature: K;
  threshold: number;
  impurity: number;
}

export function bestSplit<K extends string>(
  x: readonly Record<K, number>[],
  t: readonly string[],
): Split<K> | null {
  if (gini(t) === 0) {
    return null;
  }
  const features = Object.keys(x[0] ?? {}) as K[];
  let best: Split<K> | null = null;
  for (const feature of features) {
    const pairs = x
      .map((row, i) => ({ value: row[feature], label: t[i] as string }))
      .toSorted((a, b) => a.value - b.value);
    for (let i = 1; i < pairs.length; i++) {
      const previous = pairs[i - 1] as { value: number };
      const current = pairs[i] as { value: number };
      if (current.value === previous.value) {
        continue;
      }
      const left = pairs.slice(0, i).map((pair) => pair.label);
      const right = pairs.slice(i).map((pair) => pair.label);
      const impurity =
        (left.length * gini(left) + right.length * gini(right)) / pairs.length;
      if (best === null || impurity < best.impurity) {
        best = {
          feature,
          threshold: (previous.value + current.value) / 2,
          impurity,
        };
      }
    }
  }
  return best;
}

export type Tree<K extends string> =
  | { kind: "leaf"; label: string }
  | { kind: "node"; split: Split<K>; left: Tree<K>; right: Tree<K> };

export function majority(labels: readonly string[]): string {
  let best = "";
  let bestCount = 0;
  for (const [label, count] of countLabels(labels)) {
    if (count > bestCount) {
      best = label;
      bestCount = count;
    }
  }
  return best;
}

export function buildTree<K extends string>(
  x: readonly Record<K, number>[],
  t: readonly string[],
  maxDepth: number | undefined,
): Tree<K> {
  const split = maxDepth === 0 ? null : bestSplit(x, t);
  if (split === null) {
    return { kind: "leaf", label: majority(t) };
  }
  const goesLeft = x.map((row) => row[split.feature] <= split.threshold);
  const left = goesLeft.flatMap((isLeft, i) => (isLeft ? [i] : []));
  const right = goesLeft.flatMap((isLeft, i) => (isLeft ? [] : [i]));
  const childDepth = maxDepth === undefined ? undefined : maxDepth - 1;
  return {
    kind: "node",
    split,
    left: buildTree(pick(x, left), pick(t, left), childDepth),
    right: buildTree(pick(x, right), pick(t, right), childDepth),
  };
}

function pick<T>(items: readonly T[], positions: readonly number[]): T[] {
  return positions.map((i) => items[i] as T);
}

export function predictOne<K extends string>(
  tree: Tree<K>,
  row: Record<K, number>,
): string {
  switch (tree.kind) {
    case "leaf":
      return tree.label;
    case "node":
      return row[tree.split.feature] <= tree.split.threshold
        ? predictOne(tree.left, row)
        : predictOne(tree.right, row);
    default: {
      const unreachable: never = tree;
      return unreachable;
    }
  }
}

export interface DecisionTreeOptions {
  /** 木の深さの上限。省略すると制限しない */
  maxDepth?: number;
}

export class DecisionTree<K extends string> {
  readonly maxDepth: number | undefined;
  tree: Tree<K> | null = null;

  constructor(options: DecisionTreeOptions = {}) {
    this.maxDepth = options.maxDepth;
  }

  fit(x: readonly Record<K, number>[], t: readonly string[]): this {
    this.tree = buildTree(x, t, this.maxDepth);
    return this;
  }

  predict(x: readonly Record<K, number>[]): string[] {
    const tree = this.tree;
    if (tree === null) {
      throw new Error("fit で学習してから predict を呼んでください");
    }
    return x.map((row) => predictOne(tree, row));
  }
}

export function formatTree<K extends string>(
  tree: Tree<K>,
  indent = "",
): string {
  switch (tree.kind) {
    case "leaf":
      return `${indent}${tree.label}`;
    case "node": {
      const { feature, threshold } = tree.split;
      return [
        `${indent}${feature} <= ${threshold.toFixed(4)}`,
        formatTree(tree.left, `${indent}  `),
        `${indent}${feature} > ${threshold.toFixed(4)}`,
        formatTree(tree.right, `${indent}  `),
      ].join("\n");
    }
  }
}
