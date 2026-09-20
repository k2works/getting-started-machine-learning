namespace MachineLearning.Tests.Chapter13;

using MachineLearning.Chapter07;
using MachineLearning.Chapter13;

public class PcaTests
{
    /// <summary>2 列目が 1 列目の 2 倍の、完全に相関する架空のデータ。</summary>
    private static readonly Matrix Correlated =
        Matrix.FromRows([[1.0, 2.0], [2.0, 4.0], [3.0, 6.0], [4.0, 8.0]]);

    [Fact(DisplayName = "列ごとの平均を求める")]
    public void ComputesColumnMeans() => Assert.Equal([2.5, 5.0], Pca.ColumnMeans(Correlated));

    [Fact(DisplayName = "分散共分散行列は対角に分散、それ以外に共分散を持つ")]
    public void ComputesCovarianceMatrix()
    {
        var covariance = Pca.CovarianceMatrix(Correlated);

        // 1 列目の不偏分散は ((1.5)^2 + (0.5)^2) * 2 / 3
        Assert.Equal(5.0 / 3.0, covariance[0, 0], 10);
        Assert.Equal(20.0 / 3.0, covariance[1, 1], 10);
        Assert.Equal(10.0 / 3.0, covariance[0, 1], 10);
        Assert.Equal(covariance[0, 1], covariance[1, 0], 10);
    }

    [Fact(DisplayName = "完全に相関する 2 列は、第 1 主成分だけで説明できる")]
    public void ExplainsCorrelatedColumnsWithOneComponent()
    {
        var model = Pca.Fit(2, Correlated);

        Assert.Equal(1.0, model.ExplainedVarianceRatio[0], 10);
        Assert.Equal(0.0, model.ExplainedVarianceRatio[1], 10);
        Assert.Equal([2.5, 5.0], model.Mean);
    }

    [Fact(DisplayName = "主成分は長さ 1 で、たがいに直交する")]
    public void ComponentsAreOrthonormal()
    {
        var model = Pca.Fit(3, Matrix.FromRows(
            [[1.0, 2.0, 0.5], [2.0, 1.0, 1.5], [3.0, 5.0, 0.0], [4.0, 3.0, 2.5], [5.0, 8.0, 1.0]]));

        for (var i = 0; i < model.Components.Count; i++)
        {
            Assert.Equal(1.0, Matrix.Dot(model.Components[i], model.Components[i]), 10);
            for (var j = i + 1; j < model.Components.Count; j++)
            {
                Assert.Equal(0.0, Matrix.Dot(model.Components[i], model.Components[j]), 10);
            }
        }
    }

    [Fact(DisplayName = "寄与率は大きい順に並び、合計が 1 になる")]
    public void RatiosAreSortedAndSumToOne()
    {
        var ratios = Pca.Fit(3, Matrix.FromRows(
            [[1.0, 2.0, 0.5], [2.0, 1.0, 1.5], [3.0, 5.0, 0.0], [4.0, 3.0, 2.5], [5.0, 8.0, 1.0]]))
            .ExplainedVarianceRatio;

        Assert.Equal(1.0, ratios.Sum(), 10);
        Assert.Equal(ratios.OrderByDescending(ratio => ratio), ratios);
    }

    [Fact(DisplayName = "主成分の符号は、絶対値が最大の成分が正になるようにそろえる")]
    public void NormalizesSigns()
    {
        var normalized = Pca.NormalizeSigns([[-0.8, 0.6], [0.6, 0.8]]);

        Assert.Equal([0.8, -0.6], normalized[0]);
        Assert.Equal([0.6, 0.8], normalized[1]);
    }

    [Fact(DisplayName = "変換すると、平均を引いた値を主成分の向きに射影した座標になる")]
    public void TransformsToComponentCoordinates()
    {
        var model = Pca.Fit(1, Correlated);

        var transformed = Pca.Transform(model, Correlated);

        Assert.Equal(4, transformed.RowCount);
        Assert.Equal(1, transformed.ColumnCount);
        // 第 1 主成分の座標は、平均の位置で 0 になり、順に等間隔で並ぶ
        Assert.Equal(0.0, transformed[0, 0] + transformed[3, 0], 10);
        Assert.Equal(transformed[1, 0] - transformed[0, 0], transformed[2, 0] - transformed[1, 0], 10);
    }

    [Theory(DisplayName = "累積寄与率がしきい値に届くまでの主成分の数を求める")]
    [InlineData(0.5, 1)]
    [InlineData(0.8, 2)]
    [InlineData(0.95, 3)]
    public void CountsComponentsNeeded(double threshold, int expected) =>
        Assert.Equal(expected, Pca.ComponentsNeeded(threshold, [0.6, 0.25, 0.1, 0.05]));

    [Fact(DisplayName = "主成分への影響が大きい列を、係数の絶対値の大きい順に返す")]
    public void ListsTopLoadings()
    {
        var loadings = Pca.TopLoadings(2, ["A", "B", "C"], [0.3, -0.9, 0.5]);

        Assert.Equal(["B", "C"], loadings.Select(pair => pair.Key));
        Assert.Equal([-0.9, 0.5], loadings.Select(pair => pair.Value));
    }
}
