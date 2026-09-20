namespace MachineLearning.Tests.Chapter07;

using MachineLearning.Chapter07;

public class CinemaTests : IDisposable
{
    private const string Header = "cinema_id,SNS1,SNS2,actor,original,sales\n";

    private readonly string directory = Directory.CreateTempSubdirectory("ml-csharp-").FullName;

    [Fact(DisplayName = "特徴量の列は cinema_id と sales を除いた 4 列")]
    public void FeatureColumns()
    {
        var table = Cinema.Load(this.WriteCsv("1,10,20,5000,0,8000\n"));

        Assert.Equal(["SNS1", "SNS2", "actor", "original"], Cinema.FeatureColumns(table));
    }

    [Fact(DisplayName = "SNS2 が 1000 を超え、興行収入が 8500 未満の行を外れ値として取り除く")]
    public void RemovesOutlier()
    {
        var table = Cinema.Load(this.WriteCsv("1,10,1001,5000,0,8499\n2,10,20,5000,0,8000\n"));

        var kept = Cinema.RemoveOutliers(table);

        Assert.Equal(["2"], kept.Rows.Select(row => row.Text("cinema_id")));
    }

    [Theory(DisplayName = "条件の片方だけを満たす行は残す")]
    [InlineData("1001", "8500")]
    [InlineData("1000", "8499")]
    public void KeepsRowsMatchingOneCondition(string sns2, string sales)
    {
        var table = Cinema.Load(this.WriteCsv($"1,10,{sns2},5000,0,{sales}\n"));

        Assert.Single(Cinema.RemoveOutliers(table).Rows);
    }

    [Fact(DisplayName = "SNS2 が欠損している行は外れ値かどうか判断できないので残す")]
    public void KeepsMissingSns2()
    {
        var table = Cinema.Load(this.WriteCsv("1,10,,5000,0,8000\n"));

        Assert.Single(Cinema.RemoveOutliers(table).Rows);
    }

    [Fact(DisplayName = "前処理は外れ値を除いて分割し、訓練データの平均で欠損値を補完する")]
    public void PreparesSplit()
    {
        var rows = string.Concat(Enumerable.Range(1, 10).Select(i =>
            $"{i},{(i == 1 ? string.Empty : i * 10)},{i * 20},{i * 100},{i % 2},{8000 + (i * 10)}\n"));

        var split = Cinema.Prepare(this.WriteCsv(rows), 0.2, 0);

        Assert.Equal((8, 2), (split.XTrain.Count, split.XTest.Count));
        Assert.Equal(["SNS1", "SNS2", "actor", "original"], split.XTrain[0].Columns);
        Assert.All(split.XTrain.Concat(split.XTest), features => Assert.All(features.Values, value => Assert.False(double.IsNaN(value))));
        Assert.All(split.TTrain.Concat(split.TTest), sales => Assert.InRange(sales, 8010, 8100));
    }

    public void Dispose()
    {
        Directory.Delete(this.directory, recursive: true);
        GC.SuppressFinalize(this);
    }

    private string WriteCsv(string rows)
    {
        var path = Path.Combine(this.directory, "cinema.csv");
        File.WriteAllText(path, Header + rows);
        return path;
    }
}
