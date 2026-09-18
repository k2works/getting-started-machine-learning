// ml-cross-validation は型定義を同梱しないので、本リポジトリで使う範囲だけを宣言する
declare module "ml-cross-validation" {
  /** 依存する ml-confusion-matrix 0.4 系の混同行列（使うメソッドだけ） */
  export interface CrossValidationConfusionMatrix<T> {
    getTruePositiveCount(label: T): number;
    getFalsePositiveCount(label: T): number;
    getFalseNegativeCount(label: T): number;
    getTrueNegativeCount(label: T): number;
    getAccuracy(): number;
  }

  export interface FoldIndex {
    testIndex: number[];
    trainIndex: number[];
  }

  /** Math.random で行を並べ替え、k 個の分割の行番号を返す */
  export function getFolds(
    features: readonly unknown[],
    k?: number,
  ): FoldIndex[];

  /**
   * K 分割交差検証。分類器の代わりに、訓練データで学習してテストデータの予測を返す関数を渡す形。
   * 関数は分割数 k の後ろに渡す（README に無い順序。ソースで確認した）。
   * すべての分割の予測を 1 つの混同行列に足し合わせて返す。
   */
  export function kFold<F, T>(
    features: readonly F[],
    labels: readonly T[],
    k: number,
    callback: (trainFeatures: F[], trainLabels: T[], testFeatures: F[]) => T[],
  ): CrossValidationConfusionMatrix<T>;
}
