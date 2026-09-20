namespace MachineLearning.Tests.Chapter08;

using System.Text.Json;
using MachineLearning.Chapter02;
using MachineLearning.Chapter08;

public class ModelFilesTests : IDisposable
{
    private readonly string directory = Directory.CreateTempSubdirectory("chapter08").FullName;

    private static readonly Row[] Train =
    [
        Passengers.Of("1", "female", "30", "0", "0", "50", "S"),
        Passengers.Of("1", "female", "40", "1", "0", "60", "C"),
        Passengers.Of("3", "male", "20", "0", "0", "8", "S"),
        Passengers.Of("3", "male", string.Empty, "0", "0", "7", string.Empty),
    ];

    private static readonly int[] Labels = [1, 1, 0, 0];

    [Fact(DisplayName = "保存して読み込んだパイプラインは同じ予測をする")]
    public void SavesAndLoads()
    {
        var pipeline = Pipeline.Build(2, ClassWeight.Balanced).Fit(Passengers.ToTable(Train), Labels);
        var modelFile = Path.Combine(this.directory, "survived.json");

        ModelFiles.Save(pipeline, modelFile);
        var loaded = ModelFiles.Load(modelFile);

        Assert.Equal(pipeline.Predict(Passengers.ToTable(Train)), loaded.Predict(Passengers.ToTable(Train)));
        Assert.Equal(pipeline.Model, loaded.Model);
    }

    [Fact(DisplayName = "保存先のディレクトリが無ければ作る")]
    public void CreatesDirectory()
    {
        var pipeline = Pipeline.Build(1, ClassWeight.None).Fit(Passengers.ToTable(Train), Labels);
        var modelFile = Path.Combine(this.directory, "model", "survived.json");

        ModelFiles.Save(pipeline, modelFile);

        Assert.True(File.Exists(modelFile));
    }

    [Fact(DisplayName = "木の節と葉は kind で書き分けられる")]
    public void WritesTreeKind()
    {
        var pipeline = Pipeline.Build(1, ClassWeight.None).Fit(Passengers.ToTable(Train), Labels);
        var modelFile = Path.Combine(this.directory, "survived.json");

        ModelFiles.Save(pipeline, modelFile);

        var json = File.ReadAllText(modelFile);
        Assert.Contains("\"kind\":\"split\"", json, StringComparison.Ordinal);
        Assert.Contains("\"kind\":\"leaf\"", json, StringComparison.Ordinal);
        Assert.Contains("\"type\":\"groupMedian\"", json, StringComparison.Ordinal);
    }

    [Fact(DisplayName = "形の違うファイルは読み込めない")]
    public void RejectsWrongShape()
    {
        var modelFile = Path.Combine(this.directory, "broken.json");
        File.WriteAllText(modelFile, "{\"Transformers\":[]}");

        Assert.Throws<JsonException>(() => ModelFiles.Load(modelFile));
    }

    public void Dispose()
    {
        Directory.Delete(this.directory, recursive: true);
        GC.SuppressFinalize(this);
    }
}
