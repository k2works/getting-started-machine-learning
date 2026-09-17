import { DecisionTreeClassifier } from "ml-cart";

export interface MlCartOptions {
  maxDepth?: number;
}

/**
 * ml-cart の決定木を、自作の決定木と同じ形（特徴量のレコードと文字列のラベル）で学習し、予測する関数を返す。
 * 条件をそろえるため、葉にする件数は 1、分割に必要な利得の下限は 0 にする。
 */
export function trainMlCart<K extends string>(
  x: readonly Record<K, number>[],
  t: readonly string[],
  options: MlCartOptions,
): (rows: readonly Record<K, number>[]) => string[] {
  const features = Object.keys(x[0] ?? {}) as K[];
  const toMatrix = (rows: readonly Record<K, number>[]) =>
    rows.map((row) => features.map((feature) => row[feature]));
  const classes = [...new Set(t)];
  const classifier = new DecisionTreeClassifier({
    gainFunction: "gini",
    minNumSamples: 1,
    gainThreshold: 0,
    maxDepth: options.maxDepth ?? Infinity,
  });
  classifier.train(
    toMatrix(x),
    t.map((label) => classes.indexOf(label)),
  );
  return (rows) =>
    classifier.predict(toMatrix(rows)).map((index) => classes[index] as string);
}
