namespace MachineLearning.Tests.Chapter15;

using MachineLearning.Chapter15;
using MachineLearning.Dataset;

public class TrainedModelsTests : IDisposable
{
    private readonly string directory = Directory.CreateTempSubdirectory("ml-csharp-trained-").FullName;

    [Fact(DisplayName = "学習したモデルを保存するとヘルスチェックが ok になる")]
    public void Healthy()
    {
        var store = this.TrainedStore();

        Assert.Equal(new Health(true, true), new PredictionService(store).Health());
    }

    [Fact(DisplayName = "学習した線形回帰モデルで興行収入を予測する")]
    public void PredictsSales()
    {
        var model = Assert.IsType<Success<SalesModel>>(this.TrainedStore().LoadSalesModel()).Value;

        Assert.True(model(new Movie(200.0, 500.0, 3000.0, true)) > 0);
    }

    [Fact(DisplayName = "学習したパイプラインで 1 等客室の女性は生存と予測する")]
    public void FirstClassWoman()
    {
        var model = Assert.IsType<Success<SurvivalModel>>(this.TrainedStore().LoadSurvivalModel()).Value;

        Assert.True(model(new Passenger(Pclass.First, Sex.Female, 30.0, 0, 0, 80.0, Embarked.Cherbourg)));
    }

    [Fact(DisplayName = "学習したパイプラインで 3 等客室の男性は死亡と予測する")]
    public void ThirdClassMan()
    {
        var model = Assert.IsType<Success<SurvivalModel>>(this.TrainedStore().LoadSurvivalModel()).Value;

        Assert.False(model(new Passenger(Pclass.Third, Sex.Male, 30.0, 0, 0, 8.0, Embarked.Southampton)));
    }

    [Fact(DisplayName = "学習するとモデルを保存して起動する URL を表示する")]
    public void Reports()
    {
        RequireData();
        using var output = new StringWriter();

        MachineLearning.Chapter15.Program.TrainAndReport(output, this.directory);

        Assert.Equal(
            string.Join(
                Environment.NewLine,
                "学習済みモデルを保存しました: cinema.json, survived.json",
                "API を起動します: http://127.0.0.1:8015") + Environment.NewLine,
            output.ToString());
        Assert.True(File.Exists(Path.Combine(this.directory, "cinema.json")));
        Assert.True(File.Exists(Path.Combine(this.directory, "survived.json")));
    }

    public void Dispose()
    {
        Directory.Delete(this.directory, recursive: true);
        GC.SuppressFinalize(this);
    }

    private static void RequireData()
    {
        var data = DataDir.Current();
        Assert.SkipUnless(
            File.Exists(Path.Combine(data, "cinema.csv")) && File.Exists(Path.Combine(data, "Survived.csv")),
            "学習データ cinema.csv・Survived.csv が配置されていない（gulp data:setup）");
    }

    private FileModelStore TrainedStore()
    {
        RequireData();
        var store = new FileModelStore(this.directory);
        Training.TrainAndSaveModels(DataDir.Current(), store);
        return store;
    }
}
