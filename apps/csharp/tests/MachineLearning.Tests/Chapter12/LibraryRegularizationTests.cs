namespace MachineLearning.Tests.Chapter12;

using MachineLearning.Chapter07;
using MachineLearning.Chapter12;

/// <summary>ML.NET の正則化の振る舞いを確かめる学習用テスト。</summary>
public class LibraryRegularizationTests
{
    /// <summary>反復で解く SDCA と厳密な解の差を許す幅。</summary>
    private const double Tolerance = 1e-3;

    [Fact(DisplayName = "学習用テスト: ML.NET の Sdca の L2Regularization は、自作の alpha を件数の半分で割った値に当たる")]
    public void SdcaL2ScaleMatchesAlpha()
    {
        var (x, t) = Dataset();
        const double L2 = 0.1;

        var library = MlNetRegularization.FitSdca(L2, x, t);
        var mine = Regularization.FitRidge(t.Count * L2 / 2.0, x, t);

        for (var j = 0; j < x.ColumnCount; j++)
        {
            Assert.Equal(mine.Coefficients[j], library.Coefficients[j], Tolerance);
        }

        Assert.Equal(mine.Intercept, library.Intercept, Tolerance);
    }

    [Fact(DisplayName = "学習用テスト: 同じ alpha なら ML.NET のリッジ回帰は自作とほぼ同じ係数になる")]
    public void RidgeWithMlNetMatchesFitRidge()
    {
        var (x, t) = Dataset();
        const double Alpha = 10.0;

        var library = MlNetRegularization.FitRidgeWithMlNet(Alpha, x, t);
        var mine = Regularization.FitRidge(Alpha, x, t);

        for (var j = 0; j < x.ColumnCount; j++)
        {
            Assert.Equal(mine.Coefficients[j], library.Coefficients[j], Tolerance);
        }
    }

    [Fact(DisplayName = "学習用テスト: ML.NET の SDCA はスレッドを 1 本に絞れば実行のたびに同じ結果になる")]
    public void SdcaIsReproducible()
    {
        var (x, t) = Dataset();

        var first = MlNetRegularization.FitSdca(0.1, x, t);
        var second = MlNetRegularization.FitSdca(0.1, x, t);

        Assert.Equal<IReadOnlyList<double>>(first.Coefficients, second.Coefficients);
    }

    /// <summary>係数が分かっている架空のデータ。F# 版の randomDataset と同じ作り方（学習データは使わない）。</summary>
    private static (Matrix X, IReadOnlyList<double> T) Dataset()
    {
        double[] weights = [1.5, -2.0, 0.5, 3.0];
        var random = new Random(0);
        var rows = Enumerable.Range(0, 30)
            .Select(_ => Enumerable.Range(0, weights.Length)
                .Select(_ => (random.NextDouble() * 2.0) - 1.0)
                .ToArray())
            .ToList();
        IReadOnlyList<double> t =
            [.. rows.Select(row => Matrix.Dot(row, weights) + 2.0 + (0.5 * (random.NextDouble() - 0.5)))];
        return (Matrix.FromRows([.. rows]), t);
    }
}
