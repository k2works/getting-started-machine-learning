package chapter15;

/** テスト用のモデルの置き場。 */
final class Stubs {
  private Stubs() {}

  /** 渡したモデルを返す置き場。 */
  static ModelStore store(SalesModel sales, SurvivalModel survival) {
    return new ModelStore() {
      @Override
      public SalesModel loadSalesModel() {
        return sales;
      }

      @Override
      public SurvivalModel loadSurvivalModel() {
        return survival;
      }
    };
  }

  /** どのモデルも見つからない置き場。 */
  static ModelStore emptyStore() {
    return new ModelStore() {
      @Override
      public SalesModel loadSalesModel() throws ModelNotFoundException {
        throw new ModelNotFoundException("cinema");
      }

      @Override
      public SurvivalModel loadSurvivalModel() throws ModelNotFoundException {
        throw new ModelNotFoundException("survived");
      }
    };
  }
}
