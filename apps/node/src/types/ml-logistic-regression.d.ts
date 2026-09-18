// ml-logistic-regression 2.0.0 は型定義を同梱しないので、この章で使う範囲だけを宣言する
declare module "ml-logistic-regression" {
  import type { Matrix } from "ml-matrix";

  export interface LogisticRegressionOptions {
    /** 勾配降下法の繰り返し回数。既定値は 50000 */
    numSteps?: number;
    /** 学習率。既定値は 5e-4 */
    learningRate?: number;
  }

  /** 品種ごとの 2 値分類器を組み合わせる（one-vs-rest）ロジスティック回帰。切片の項は無い */
  export default class LogisticRegression {
    constructor(options?: LogisticRegressionOptions);
    /** y は 0 始まりの整数のラベルを並べた列ベクトル */
    train(x: Matrix, y: Matrix): void;
    predict(x: Matrix): number[];
  }
}
