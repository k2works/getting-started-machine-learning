function sumWeightsByLabel(
  labels: readonly number[],
  weights: readonly number[],
): Map<number, number> {
  const sums = new Map<number, number>();
  labels.forEach((label, i) => {
    sums.set(label, (sums.get(label) ?? 0) + (weights[i] as number));
  });
  return sums;
}

function sum(values: readonly number[]): number {
  return values.reduce((total, value) => total + value, 0);
}

export function weightedGini(
  labels: readonly number[],
  weights: readonly number[],
): number {
  const total = sum(weights);
  let sumOfSquares = 0;
  for (const weight of sumWeightsByLabel(labels, weights).values()) {
    sumOfSquares += (weight / total) ** 2;
  }
  return 1 - sumOfSquares;
}

/** クラスの件数に反比例する重み（件数 / (クラスの数 × そのクラスの件数)）を 1 件ごとに求める */
export function balancedWeights(t: readonly number[]): number[] {
  const counts = sumWeightsByLabel(
    t,
    t.map(() => 1),
  );
  return t.map(
    (label) => t.length / (counts.size * (counts.get(label) as number)),
  );
}

export const CLASS_WEIGHTS = ["none", "balanced"] as const;
export type ClassWeight = (typeof CLASS_WEIGHTS)[number];

export type WeightedTree =
  | { kind: "leaf"; label: number }
  | {
      kind: "node";
      feature: string;
      threshold: number;
      left: WeightedTree;
      right: WeightedTree;
    };

interface Candidate {
  feature: string;
  threshold: number;
  impurity: number;
}

function bestSplit(
  x: readonly Record<string, number>[],
  t: readonly number[],
  w: readonly number[],
): Candidate | null {
  if (weightedGini(t, w) === 0) {
    return null;
  }
  const total = sum(w);
  let best: Candidate | null = null;
  for (const feature of Object.keys(x[0] ?? {})) {
    const order = x
      .map((row, i) => ({ value: row[feature] as number, i }))
      .toSorted((a, b) => a.value - b.value);
    const labels = order.map(({ i }) => t[i] as number);
    const weights = order.map(({ i }) => w[i] as number);
    for (let i = 1; i < order.length; i++) {
      const previous = (order[i - 1] as { value: number }).value;
      const current = (order[i] as { value: number }).value;
      if (current === previous) {
        continue;
      }
      const left = weights.slice(0, i);
      const right = weights.slice(i);
      const impurity =
        (sum(left) * weightedGini(labels.slice(0, i), left) +
          sum(right) * weightedGini(labels.slice(i), right)) /
        total;
      if (best === null || impurity < best.impurity) {
        best = { feature, threshold: (previous + current) / 2, impurity };
      }
    }
  }
  return best;
}

function weightedMajority(t: readonly number[], w: readonly number[]): number {
  let best = 0;
  let bestWeight = 0;
  for (const [label, weight] of sumWeightsByLabel(t, w)) {
    if (weight > bestWeight) {
      best = label;
      bestWeight = weight;
    }
  }
  return best;
}

function pick<T>(items: readonly T[], positions: readonly number[]): T[] {
  return positions.map((i) => items[i] as T);
}

function buildTree(
  x: readonly Record<string, number>[],
  t: readonly number[],
  w: readonly number[],
  maxDepth: number | undefined,
): WeightedTree {
  const split = maxDepth === 0 ? null : bestSplit(x, t, w);
  if (split === null) {
    return { kind: "leaf", label: weightedMajority(t, w) };
  }
  const { feature, threshold } = split;
  const goesLeft = x.map((row) => (row[feature] as number) <= threshold);
  const left = goesLeft.flatMap((isLeft, i) => (isLeft ? [i] : []));
  const right = goesLeft.flatMap((isLeft, i) => (isLeft ? [] : [i]));
  const childDepth = maxDepth === undefined ? undefined : maxDepth - 1;
  return {
    kind: "node",
    feature,
    threshold,
    left: buildTree(pick(x, left), pick(t, left), pick(w, left), childDepth),
    right: buildTree(
      pick(x, right),
      pick(t, right),
      pick(w, right),
      childDepth,
    ),
  };
}

export interface DecisionTreeOptions {
  /** 木の深さの上限。undefined なら制限しない */
  maxDepth: number | undefined;
  classWeight: ClassWeight;
}

export function fitDecisionTree(
  x: readonly Record<string, number>[],
  t: readonly number[],
  options: DecisionTreeOptions,
): WeightedTree {
  return buildTree(
    x,
    t,
    classWeights(t, options.classWeight),
    options.maxDepth,
  );
}

function classWeights(
  t: readonly number[],
  classWeight: ClassWeight,
): number[] {
  switch (classWeight) {
    case "none":
      return t.map(() => 1);
    case "balanced":
      return balancedWeights(t);
    default: {
      const unreachable: never = classWeight;
      return unreachable;
    }
  }
}

function predictOne(tree: WeightedTree, row: Record<string, number>): number {
  switch (tree.kind) {
    case "leaf":
      return tree.label;
    case "node":
      return (row[tree.feature] as number) <= tree.threshold
        ? predictOne(tree.left, row)
        : predictOne(tree.right, row);
    default: {
      const unreachable: never = tree;
      return unreachable;
    }
  }
}

export function predictDecisionTree(
  tree: WeightedTree,
  x: readonly Record<string, number>[],
): number[] {
  return x.map((row) => predictOne(tree, row));
}
