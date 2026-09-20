namespace MachineLearning.Tests.Chapter13;

using MachineLearning.Chapter02;
using MachineLearning.Chapter13;

public class BostonStandardizedTests
{
    /// <summary>架空の値の表。CRIME はカテゴリ、ZN は欠損値を含む数値、PRICE は数値。</summary>
    private static Table Sample() =>
        new(
            ["CRIME", "ZN", "PRICE"],
            [
                Row(("CRIME", "high"), ("ZN", "10"), ("PRICE", "20")),
                Row(("CRIME", "low"), ("ZN", "20"), ("PRICE", "30")),
                Row(("CRIME", "low"), ("ZN", ""), ("PRICE", "40")),
                Row(("CRIME", "very_low"), ("ZN", "30"), ("PRICE", "50")),
            ]);

    [Fact(DisplayName = "CRIME を先頭を除いたダミー変数の列にして、末尾に並べる")]
    public void EncodesCategoryColumn()
    {
        var table = BostonStandardized.Standardize(Sample());

        Assert.Equal(["ZN", "PRICE", "CRIME_low", "CRIME_very_low"], table.Columns);
        Assert.Equal(4, table.X.RowCount);
    }

    [Fact(DisplayName = "すべての列が平均 0・標準偏差 1 になる")]
    public void StandardizesEveryColumn()
    {
        var x = BostonStandardized.Standardize(Sample()).X;

        for (var column = 0; column < x.ColumnCount; column++)
        {
            var values = Enumerable.Range(0, x.RowCount).Select(row => x[row, column]).ToList();
            Assert.Equal(0.0, values.Average(), 10);
            Assert.Equal(1.0, Math.Sqrt(values.Average(value => value * value)), 10);
        }
    }

    [Fact(DisplayName = "欠損値は列の平均値で補完する")]
    public void FillsMissingWithColumnMean()
    {
        var x = BostonStandardized.Standardize(Sample()).X;

        // ZN の平均は (10 + 20 + 30) / 3 = 20 なので、補完した 3 行目は標準化しても 0 になる
        Assert.Equal(0.0, x[2, 0], 10);
    }

    private static Row Row(params (string Column, string Value)[] cells) =>
        new(cells.ToDictionary(cell => cell.Column, cell => cell.Value, StringComparer.Ordinal));
}
