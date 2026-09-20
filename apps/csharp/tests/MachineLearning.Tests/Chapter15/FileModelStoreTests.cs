namespace MachineLearning.Tests.Chapter15;

using MachineLearning.Chapter02;
using MachineLearning.Chapter07;
using MachineLearning.Chapter15;

public class FileModelStoreTests : IDisposable
{
    private readonly string directory = Directory.CreateTempSubdirectory("ml-csharp-model-").FullName;

    [Fact(DisplayName = "保存した線形回帰モデルを読み込んで興行収入を予測する")]
    public void SalesModel()
    {
        var store = new FileModelStore(this.directory);
        var columns = new[] { "SNS1", "SNS2", "actor", "original" };
        store.SaveSalesModel(new LinearModel(100.0, new Features(columns, [1.0, 2.0, 0.5, 10.0])));

        var model = Assert.IsType<Success<SalesModel>>(store.LoadSalesModel()).Value;

        Assert.Equal(210.0, model(new Movie(10.0, 20.0, 100.0, true)), 9);
    }

    [Fact(DisplayName = "モデルのファイルが無ければ ModelNotFound を返す")]
    public void MissingFile()
    {
        var store = new FileModelStore(this.directory);

        var error = Assert.IsType<Failure<SalesModel>>(store.LoadSalesModel()).Error;

        Assert.Equal(new ModelNotFound("cinema"), error);
        Assert.DoesNotContain(this.directory, error.Describe(), StringComparison.Ordinal);
    }

    [Fact(DisplayName = "モデルのファイルが壊れていれば ModelUnreadable を返す")]
    public void BrokenFile()
    {
        File.WriteAllText(Path.Combine(this.directory, "cinema.json"), "{");

        var error = Assert.IsType<Failure<SalesModel>>(new FileModelStore(this.directory).LoadSalesModel()).Error;

        Assert.Equal(new ModelUnreadable("cinema"), error);
    }

    public void Dispose()
    {
        Directory.Delete(this.directory, recursive: true);
        GC.SuppressFinalize(this);
    }
}
