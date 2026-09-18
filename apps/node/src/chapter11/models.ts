import MultivariateLinearRegression from "ml-regression-multivariate-linear";
import { DecisionTree } from "../chapter03/decision-tree.ts";
import type { Model } from "./cross-validation.ts";

/** 第 3 章の決定木を第 11 章の Model として使うアダプター */
export class DecisionTreeModel<K extends string> implements Model<
  Record<K, number>,
  string
> {
  private readonly tree: DecisionTree<K>;

  constructor(maxDepth?: number) {
    this.tree = new DecisionTree<K>({ maxDepth });
  }

  fit(x: readonly Record<K, number>[], t: readonly string[]): void {
    this.tree.fit(x, t);
  }

  predict(x: readonly Record<K, number>[]): string[] {
    return this.tree.predict(x);
  }
}

/** ml-regression-multivariate-linear の線形回帰を第 11 章の Model として使うアダプター */
export class LinearRegressionModel<K extends string> implements Model<
  Record<K, number>,
  number
> {
  private features: K[] = [];
  private regression: MultivariateLinearRegression | null = null;

  fit(x: readonly Record<K, number>[], t: readonly number[]): void {
    this.features = Object.keys(x[0] ?? {}) as K[];
    this.regression = new MultivariateLinearRegression(
      this.toMatrix(x),
      t.map((value) => [value]),
    );
  }

  predict(x: readonly Record<K, number>[]): number[] {
    const regression = this.regression;
    if (regression === null) {
      throw new Error("fit で学習してから predict を呼んでください");
    }
    return regression.predict(this.toMatrix(x)).map(([y]) => y as number);
  }

  private toMatrix(rows: readonly Record<K, number>[]): number[][] {
    return rows.map((row) => this.features.map((feature) => row[feature]));
  }
}
