namespace MachineLearning.Tests.Chapter09;

using MachineLearning.Chapter02;
using MachineLearning.Chapter09;

public class DummiesTests
{
    [Fact(DisplayName = "先頭を除いたカテゴリを辞書順に返す")]
    public void DropsFirstCategory() =>
        Assert.Equal(["low", "very_low"], Dummies.Categories(["low", "high", "very_low", "low"]));

    [Fact(DisplayName = "空欄はカテゴリにしない")]
    public void IgnoresBlank() =>
        Assert.Equal(["low"], Dummies.Categories(["high", string.Empty, "low"]));

    [Fact(DisplayName = "元の列を取り除き、カテゴリごとの 0 と 1 の列を末尾に加える")]
    public void EncodesColumns()
    {
        var table = Samples.Table(
            ["CRIME", "PRICE"],
            ["high", "27.5"],
            ["low", "13.2"]);

        var encoded = Dummies.Encode(table, "CRIME", ["low", "very_low"]);

        Assert.Equal(["PRICE", "CRIME_low", "CRIME_very_low"], encoded.Columns);
        Assert.Equal(0.0, encoded.Rows[0].Number("CRIME_low"));
        Assert.Equal(1.0, encoded.Rows[1].Number("CRIME_low"));
    }

    [Fact(DisplayName = "カテゴリに無い値はすべての列が 0 になる")]
    public void UnknownCategoryIsAllZero()
    {
        var encoded = Dummies.Encode(Samples.Table(["CRIME"], ["unknown"]), "CRIME", ["low", "very_low"]);

        Assert.Equal([0.0, 0.0], encoded.Columns.Select(column => encoded.Rows[0].Number(column)));
    }
}
