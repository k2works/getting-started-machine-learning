import {
  type ModelStore,
  type Movie,
  type Passenger,
  type SalesModel,
  type SurvivalModel,
  ModelNotFoundError,
} from "../../src/chapter15/domain.ts";

/** sns1 に 1000 を足すだけの興行収入のモデル */
export const stubSalesModel: SalesModel = {
  predictSales: (movie) => 1000 + movie.sns1,
};

/** 女性なら生存と判定するだけのモデル */
export const stubSurvivalModel: SurvivalModel = {
  survives: (passenger) => passenger.sex === "female",
};

/** 常にスタブのモデルを返す置き場 */
export const stubModelStore: ModelStore = {
  loadSalesModel: () => ({ ok: true, value: stubSalesModel }),
  loadSurvivalModel: () => ({ ok: true, value: stubSurvivalModel }),
};

/** モデルが 1 つも無い置き場 */
export const emptyModelStore: ModelStore = {
  loadSalesModel: () => ({
    ok: false,
    error: new ModelNotFoundError("cinema"),
  }),
  loadSurvivalModel: () => ({
    ok: false,
    error: new ModelNotFoundError("survived"),
  }),
};

export const MOVIE: Movie = { sns1: 200, sns2: 500, actor: 3000, original: 1 };
export const PASSENGER: Passenger = {
  pclass: 1,
  sex: "female",
  age: null,
  sibSp: 0,
  parch: 0,
  fare: 50,
  embarked: "C",
};
