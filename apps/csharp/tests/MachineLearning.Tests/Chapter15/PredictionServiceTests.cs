namespace MachineLearning.Tests.Chapter15;

using MachineLearning.Chapter15;

public class PredictionServiceTests
{
    private static readonly Movie TestMovie = new(100.0, 200.0, 300.0, true);
    private static readonly Passenger TestPassenger =
        new(Pclass.First, Sex.Female, 30.0, 0, 0, 50.0, Embarked.Southampton);

    [Fact(DisplayName = "映画の特徴量から興行収入を予測する")]
    public void PredictsSales()
    {
        var service = new PredictionService(StubStore.With(_ => 1234.5, _ => true));

        var result = service.PredictSales(TestMovie);

        Assert.Equal(new Success<SalesPrediction>(new SalesPrediction(1234.5)), result);
    }

    [Fact(DisplayName = "生存と判定されれば生存と予測する")]
    public void Survives()
    {
        var service = new PredictionService(StubStore.With(_ => 0.0, _ => true));

        Assert.Equal(new Success<SurvivalPrediction>(new SurvivalPrediction(true)), service.PredictSurvival(TestPassenger));
    }

    [Fact(DisplayName = "死亡と判定されれば死亡と予測する")]
    public void Dies()
    {
        var service = new PredictionService(StubStore.With(_ => 0.0, _ => false));

        Assert.Equal(new Success<SurvivalPrediction>(new SurvivalPrediction(false)), service.PredictSurvival(TestPassenger));
    }

    [Fact(DisplayName = "モデルが無ければ ModelNotFound の失敗を返す")]
    public void MissingModel()
    {
        var service = new PredictionService(StubStore.Empty());

        var error = Assert.IsType<Failure<SalesPrediction>>(service.PredictSales(TestMovie)).Error;

        Assert.Equal(new ModelNotFound("cinema"), error);
        Assert.Equal("学習済みモデル cinema が見つかりません", error.Describe());
    }

    [Fact(DisplayName = "モデルを読み込めなければ ModelUnreadable の失敗を返す")]
    public void UnreadableModel()
    {
        var service = new PredictionService(StubStore.Unreadable());

        var error = Assert.IsType<Failure<SurvivalPrediction>>(service.PredictSurvival(TestPassenger)).Error;

        Assert.Equal("学習済みモデル survived を読み込めません", error.Describe());
    }

    [Fact(DisplayName = "モデルを読み込めればどちらも true を返す")]
    public void Healthy() =>
        Assert.Equal(new Health(true, true), new PredictionService(StubStore.With(_ => 0.0, _ => true)).Health());

    [Fact(DisplayName = "モデルが無ければどちらも false を返す")]
    public void Unhealthy() => Assert.Equal(new Health(false, false), new PredictionService(StubStore.Empty()).Health());
}
