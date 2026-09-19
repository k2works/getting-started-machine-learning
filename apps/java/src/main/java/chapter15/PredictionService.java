package chapter15;

import java.util.LinkedHashMap;
import java.util.Map;

/** 学習済みモデルを置き場から読み込んで予測する。HTTP には依存しない。 */
public final class PredictionService {
  private final ModelStore store;

  public PredictionService(ModelStore store) {
    this.store = store;
  }

  public SalesPrediction predictSales(Movie movie) throws ModelNotFoundException {
    return new SalesPrediction(store.loadSalesModel().predictSales(movie));
  }

  public SurvivalPrediction predictSurvival(Passenger passenger) throws ModelNotFoundException {
    return new SurvivalPrediction(store.loadSurvivalModel().survives(passenger));
  }

  /** モデルごとに、読み込めるかどうかを返す。 */
  public Map<String, Boolean> health() {
    Map<String, Boolean> models = new LinkedHashMap<>();
    models.put("cinema", canLoad(store::loadSalesModel));
    models.put("survived", canLoad(store::loadSurvivalModel));
    return models;
  }

  @FunctionalInterface
  private interface Loader {
    Object load() throws ModelNotFoundException;
  }

  private static boolean canLoad(Loader loader) {
    try {
      loader.load();
      return true;
    } catch (ModelNotFoundException e) {
      return false;
    }
  }
}
