import LogisticRegression, {
  type LogisticRegressionOptions,
} from "ml-logistic-regression";
import { Matrix } from "ml-matrix";
import { RandomForestClassifier } from "ml-random-forest";
import type { Classifier } from "./classifier.ts";
import { toRows } from "./logistic-regression.ts";

/** 数値の行列と 0 始まりの整数のラベルで学習・予測する ml.js のモデル */
export interface NumericModel {
  train(x: number[][], y: number[]): void;
  predict(x: number[][]): number[];
}

/** ml.js のモデルを、特徴量のレコードと文字列のラベルで使える Classifier に包む */
export class MlClassifier<K extends string> implements Classifier<K> {
  private readonly createModel: () => NumericModel;
  private model: NumericModel | null = null;
  private features: K[] = [];
  private classes: string[] = [];

  constructor(createModel: () => NumericModel) {
    this.createModel = createModel;
  }

  fit(x: readonly Record<K, number>[], t: readonly string[]): this {
    this.features = Object.keys(x[0] ?? {}) as K[];
    this.classes = [...new Set(t)].toSorted();
    const model = this.createModel();
    model.train(
      toRows(x, this.features),
      t.map((label) => this.classes.indexOf(label)),
    );
    this.model = model;
    return this;
  }

  predict(x: readonly Record<K, number>[]): string[] {
    if (this.model === null) {
      throw new Error("fit で学習してから predict を呼んでください");
    }
    return this.model
      .predict(toRows(x, this.features))
      .map((index) => this.classes[index] as string);
  }
}

export interface MlLogisticRegressionOptions extends LogisticRegressionOptions {
  /** true なら値が 1 の列を足して、切片の代わりにする。省略すると false */
  intercept?: boolean;
}

export function mlLogisticRegression<K extends string>(
  options: MlLogisticRegressionOptions = {},
): MlClassifier<K> {
  const { intercept = false, ...modelOptions } = options;
  const toMatrix = (x: number[][]) =>
    new Matrix(intercept ? x.map((row) => [...row, 1]) : x);
  return new MlClassifier(() => {
    const model = new LogisticRegression(modelOptions);
    return {
      train: (x, y) => model.train(toMatrix(x), Matrix.columnVector(y)),
      predict: (x) => model.predict(toMatrix(x)),
    };
  });
}

export interface MlRandomForestOptions {
  nEstimators: number;
  maxFeatures: number;
  maxDepth?: number;
  seed: number;
}

/**
 * ml-random-forest のランダムフォレスト。自作と条件をそろえるため、木ごとに特徴量を重複なしで選び、
 * 内側の ml-cart の決定木は 1 件になるまで分ける。使わない袋外の予測は計算しない。
 */
export function mlRandomForest<K extends string>(
  options: MlRandomForestOptions,
): MlClassifier<K> {
  return new MlClassifier(
    () =>
      new RandomForestClassifier({
        nEstimators: options.nEstimators,
        maxFeatures: options.maxFeatures,
        seed: options.seed,
        replacement: false,
        noOOB: true,
        treeOptions: {
          gainFunction: "gini",
          minNumSamples: 1,
          gainThreshold: 0,
          maxDepth: options.maxDepth ?? Infinity,
        },
      }),
  );
}
