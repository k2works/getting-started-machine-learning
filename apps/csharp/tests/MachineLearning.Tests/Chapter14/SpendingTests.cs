namespace MachineLearning.Tests.Chapter14;

using MachineLearning.Chapter02;
using MachineLearning.Chapter14;

public class SpendingTests
{
    /// <summary>架空の支出額。1 列目だけが違い、ほかの列は同じ値にしてある。</summary>
    private static IReadOnlyList<Features> Rows() =>
        [Row(100, 10), Row(200, 10), Row(300, 10)];

    [Fact(DisplayName = "標準化すると、列ごとに平均 0・標準偏差 1 になる")]
    public void StandardizesEachColumn()
    {
        var points = Spending.ToStandardizedPoints(Rows());

        var first = points.Select(point => point[0]).ToList();
        Assert.Equal(0.0, first.Average(), 10);
        Assert.Equal(1.0, Math.Sqrt(first.Average(value => value * value)), 10);
    }

    [Fact(DisplayName = "すべて同じ値の列は、標準化すると 0 になる")]
    public void ZeroesConstantColumn() =>
        Assert.All(Spending.ToStandardizedPoints(Rows()), point => Assert.Equal(0.0, point[1]));

    [Fact(DisplayName = "クラスタごとに、件数と標準化する前の平均支出額を求める")]
    public void SummarizesClusters()
    {
        var summaries = Spending.SummarizeClusters([1, 0, 1], Rows());

        // 件数の多い順に並ぶので、2 件のクラスタ 1 が先に来る
        Assert.Equal([1, 0], summaries.Select(summary => summary.Cluster));
        Assert.Equal([2, 1], summaries.Select(summary => summary.Count));
        Assert.Equal(200.0, summaries[0].Means["Fresh"]);
        Assert.Equal(200.0, summaries[1].Means["Fresh"]);
    }

    private static Features Row(double fresh, double others) =>
        new(Spending.Columns, [fresh, .. Enumerable.Repeat(others, Spending.Columns.Length - 1)]);
}
