namespace MachineLearning.Chapter15;

/// <summary>アプリケーション層。置き場からモデルを読み込んで予測するユースケースと、ヘルスチェック。</summary>
public sealed class PredictionService
{
    private readonly IModelStore store;

    public PredictionService(IModelStore store) => this.store = store;

    public PredictionResult<SalesPrediction> PredictSales(Movie movie) =>
        this.store.LoadSalesModel().Map(model => new SalesPrediction(model(movie)));

    public PredictionResult<SurvivalPrediction> PredictSurvival(Passenger passenger) =>
        this.store.LoadSurvivalModel().Map(model => new SurvivalPrediction(model(passenger)));

    /// <summary>モデルごとに、読み込めるかどうかを返す。</summary>
    public Health Health() =>
        new(this.store.LoadSalesModel().IsSuccess, this.store.LoadSurvivalModel().IsSuccess);
}

/// <summary>モデルごとに読み込めるかどうか。</summary>
public sealed record Health(bool Cinema, bool Survived);
