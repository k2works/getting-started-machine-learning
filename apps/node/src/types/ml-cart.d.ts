// ml-cart は型定義を同梱しないので、本リポジトリで使う範囲だけを宣言する
declare module "ml-cart" {
  export interface DecisionTreeClassifierOptions {
    /** 分割の基準。ml-cart 2.1.1 は "gini" だけ */
    gainFunction?: "gini";
    /** 境界の決め方。ml-cart 2.1.1 は隣り合う値の平均（"mean"）だけ */
    splitFunction?: "mean";
    /** 件数がこの値以下になったら分割せずに葉にする（既定値 3） */
    minNumSamples?: number;
    maxDepth?: number;
  }

  export class DecisionTreeClassifier {
    constructor(options?: DecisionTreeClassifierOptions);
    /** 正解ラベルは 0 始まりの整数 */
    train(trainingSet: number[][], trainingLabels: number[]): void;
    predict(toPredict: number[][]): number[];
  }
}
