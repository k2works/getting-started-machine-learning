namespace MachineLearning.Tests.Chapter02;

using MachineLearning.Chapter02;

public class TableTests : IDisposable
{
    private const string Header = "\uFEFFがく片長さ,がく片幅,花弁長さ,花弁幅,種類\n";

    private readonly string directory = Directory.CreateTempSubdirectory("ml-csharp-").FullName;

    private string WriteCsv(string rows)
    {
        var path = Path.Combine(this.directory, "iris.csv");
        File.WriteAllText(path, Header + rows);
        return path;
    }

    [Fact(DisplayName = "CSV を読み込むと列名の並びを保つ")]
    public void KeepsColumnOrder()
    {
        var table = Table.Load(this.WriteCsv("0.1,0.2,0.3,0.4,Iris-setosa\n"));

        Assert.Equal(["がく片長さ", "がく片幅", "花弁長さ", "花弁幅", "種類"], table.Columns);
    }

    [Fact(DisplayName = "空欄は欠損値（null）として読み込む")]
    public void BlankIsMissing()
    {
        var table = Table.Load(this.WriteCsv("0.1,,0.3,0.4,Iris-setosa\n"));

        var row = table.Rows[0];
        Assert.Null(row.Number("がく片幅"));
        Assert.Equal(0.1, row.Number("がく片長さ"));
        Assert.Equal("Iris-setosa", row.Text("種類"));
    }

    [Fact(DisplayName = "行の最後の列が空欄でも欠損値として読み込む")]
    public void TrailingBlankIsMissing()
    {
        var table = Table.Load(this.WriteCsv("0.1,0.2,0.3,,\n"));

        Assert.Null(table.Rows[0].Number("花弁幅"));
        Assert.Equal(string.Empty, table.Rows[0].Text("種類"));
    }

    [Fact(DisplayName = "無い列を読み出すと列名を示すエラーになる")]
    public void UnknownColumn()
    {
        var row = new Row(new Dictionary<string, string> { ["がく片長さ"] = "0.1" });

        var error = Assert.Throws<ArgumentException>(() => row.Number("花弁幅"));
        Assert.Contains("花弁幅", error.Message, StringComparison.Ordinal);
    }

    [Fact(DisplayName = "Row は受け取った辞書を写して持ち、元の辞書の変更の影響を受けない")]
    public void CopiesCells()
    {
        var cells = new Dictionary<string, string> { ["がく片長さ"] = "0.1" };
        var row = new Row(cells);

        cells["がく片長さ"] = "0.9";

        Assert.Equal(0.1, row.Number("がく片長さ"));
    }

    [Fact(DisplayName = "列ごとの欠損値の数を列の順に数える")]
    public void CountsMissing()
    {
        var table = Table.Load(this.WriteCsv("0.1,0.2,0.3,0.4,Iris-setosa\n,0.3,0.5,0.6,Iris-setosa\n,,0.7,0.8,Iris-virginica\n"));

        Assert.Equal(
            [("がく片長さ", 2), ("がく片幅", 1), ("花弁長さ", 0), ("花弁幅", 0), ("種類", 0)],
            table.CountMissing().Select(pair => (pair.Key, pair.Value)));
    }

    public void Dispose()
    {
        Directory.Delete(this.directory, recursive: true);
        GC.SuppressFinalize(this);
    }
}
