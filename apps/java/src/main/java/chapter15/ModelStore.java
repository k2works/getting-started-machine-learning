package chapter15;

/** 学習済みモデルの置き場の約束。読み込めなければ ModelNotFoundException を投げる。 */
public interface ModelStore {
  SalesModel loadSalesModel() throws ModelNotFoundException;

  SurvivalModel loadSurvivalModel() throws ModelNotFoundException;
}
