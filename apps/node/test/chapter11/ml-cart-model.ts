import { trainMlCart } from "../../src/chapter03/ml-cart-adapter.ts";
import type { Model } from "../../src/chapter11/cross-validation.ts";

/** ml-cart の決定木を第 11 章の Model として使うテスト用のアダプター */
export class MlCartTree<K extends string> implements Model<
  Record<K, number>,
  string
> {
  private readonly maxDepth: number;
  private predictor: ((rows: readonly Record<K, number>[]) => string[]) | null =
    null;

  constructor(maxDepth: number) {
    this.maxDepth = maxDepth;
  }

  fit(x: readonly Record<K, number>[], t: readonly string[]): void {
    this.predictor = trainMlCart(x, t, { maxDepth: this.maxDepth });
  }

  predict(x: readonly Record<K, number>[]): string[] {
    if (this.predictor === null) {
      throw new Error("fit で学習してから predict を呼んでください");
    }
    return this.predictor(x);
  }
}
