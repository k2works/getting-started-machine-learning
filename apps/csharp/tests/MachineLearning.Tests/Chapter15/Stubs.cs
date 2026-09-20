namespace MachineLearning.Tests.Chapter15;

using MachineLearning.Chapter15;

/// <summary>テスト用のモデルの置き場。</summary>
internal sealed class StubStore : IModelStore
{
    private readonly PredictionResult<SalesModel> sales;
    private readonly PredictionResult<SurvivalModel> survival;

    private StubStore(PredictionResult<SalesModel> sales, PredictionResult<SurvivalModel> survival)
    {
        this.sales = sales;
        this.survival = survival;
    }

    /// <summary>渡したモデルを返す置き場。</summary>
    internal static StubStore With(SalesModel sales, SurvivalModel survival) =>
        new(new Success<SalesModel>(sales), new Success<SurvivalModel>(survival));

    /// <summary>どのモデルも見つからない置き場。</summary>
    internal static StubStore Empty() =>
        new(new Failure<SalesModel>(new ModelNotFound("cinema")), new Failure<SurvivalModel>(new ModelNotFound("survived")));

    /// <summary>モデルのファイルはあるが読み込めない置き場。</summary>
    internal static StubStore Unreadable() =>
        new(new Failure<SalesModel>(new ModelUnreadable("cinema")), new Failure<SurvivalModel>(new ModelUnreadable("survived")));

    public PredictionResult<SalesModel> LoadSalesModel() => this.sales;

    public PredictionResult<SurvivalModel> LoadSurvivalModel() => this.survival;
}
