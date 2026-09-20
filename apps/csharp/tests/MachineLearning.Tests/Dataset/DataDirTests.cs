namespace MachineLearning.Tests.Dataset;

using MachineLearning.Dataset;

public class DataDirTests
{
    [Fact(DisplayName = "環境変数 ML_DATA_DIR が指定されていればそのディレクトリを返す")]
    public void UsesEnvironmentVariable()
    {
        var env = new Dictionary<string, string> { ["ML_DATA_DIR"] = "/tmp/ml-data" };

        Assert.Equal("/tmp/ml-data", DataDir.From(name => env.GetValueOrDefault(name)));
    }

    [Fact(DisplayName = "環境変数が無ければ apps/data/sukkiri-ml を返す")]
    public void DefaultsToAppsData()
    {
        var directory = DataDir.From(name => null);

        Assert.EndsWith(Path.Combine("apps", "data", "sukkiri-ml"), Path.GetFullPath(directory), StringComparison.Ordinal);
    }
}
