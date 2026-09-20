package machinelearning.chapter15

/** アプリケーション層。置き場からモデルを読み込んで予測するユースケースと、ヘルスチェック。 ドメイン層だけを知る。
  */
class PredictionService(store: ModelStore):
  def predictSales(movie: Movie): Either[PredictionError, SalesPrediction] =
    store.loadSalesModel().map(model => SalesPrediction(model(movie)))

  def predictSurvival(passenger: Passenger): Either[PredictionError, SurvivalPrediction] =
    store.loadSurvivalModel().map(model => SurvivalPrediction(model(passenger)))

  /** モデルごとに、読み込めるかどうかを返す。 */
  def health: Health =
    Health(store.loadSalesModel().isRight, store.loadSurvivalModel().isRight)

/** モデルごとに読み込めるかどうか。 */
case class Health(cinema: Boolean, survived: Boolean)
