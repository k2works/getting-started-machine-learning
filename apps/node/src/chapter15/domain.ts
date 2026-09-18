export interface Movie {
  sns1: number;
  sns2: number;
  actor: number;
  original: number;
}

export interface Passenger {
  pclass: number;
  sex: string;
  age: number | null;
  sibSp: number;
  parch: number;
  fare: number;
  embarked: string | null;
}

export interface SalesPrediction {
  sales: number;
}

export interface SurvivalPrediction {
  survived: boolean;
}

/** 成功した値か、失敗の原因のどちらかを持つ */
export type Result<T> = { ok: true; value: T } | { ok: false; error: Error };

/** 学習済みモデルが見つからないことを表す。メッセージにはファイルのパスを含めない */
export class ModelNotFoundError extends Error {
  readonly model: string;

  constructor(model: string) {
    super(`学習済みモデル ${model} が見つかりません`);
    this.name = "ModelNotFoundError";
    this.model = model;
  }
}

/** 映画の特徴量から興行収入を予測するモデルの約束 */
export interface SalesModel {
  predictSales(movie: Movie): number;
}

/** 乗客が生存するかを判定するモデルの約束 */
export interface SurvivalModel {
  survives(passenger: Passenger): boolean;
}

/** 学習済みモデルの置き場の約束。読み込めなければ失敗の Result を返す */
export interface ModelStore {
  loadSalesModel(): Result<SalesModel>;
  loadSurvivalModel(): Result<SurvivalModel>;
}
