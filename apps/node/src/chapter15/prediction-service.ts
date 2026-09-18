import type {
  ModelStore,
  Movie,
  Passenger,
  Result,
  SalesPrediction,
  SurvivalPrediction,
} from "./domain.ts";

/** 成功していれば値を変換し、失敗していれば失敗をそのまま返す */
function mapResult<T, U>(
  result: Result<T>,
  transform: (value: T) => U,
): Result<U> {
  return result.ok ? { ok: true, value: transform(result.value) } : result;
}

export class PredictionService {
  readonly #store: ModelStore;

  constructor(store: ModelStore) {
    this.#store = store;
  }

  predictSales(movie: Movie): Result<SalesPrediction> {
    return mapResult(this.#store.loadSalesModel(), (model) => ({
      sales: model.predictSales(movie),
    }));
  }

  predictSurvival(passenger: Passenger): Result<SurvivalPrediction> {
    return mapResult(this.#store.loadSurvivalModel(), (model) => ({
      survived: model.survives(passenger),
    }));
  }

  health(): Record<"cinema" | "survived", boolean> {
    return {
      cinema: this.#store.loadSalesModel().ok,
      survived: this.#store.loadSurvivalModel().ok,
    };
  }
}
