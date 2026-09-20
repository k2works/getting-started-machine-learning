namespace MachineLearning.Tests.Chapter09;

using MachineLearning.Chapter09;

public class MlNetNormalizationTests
{
    private static readonly double[] Values = [1.0, 2.0, 3.0, 6.0];

    [Fact(DisplayName = "学習用テスト: NormalizeMeanVariance の既定（fixZero）は平均を引かず、どの値にも同じ数を掛けるだけ")]
    public void FixZeroKeepsZero()
    {
        var normalized = MlNetNormalization.NormalizeMeanVariance(true, Values);

        var ratios = normalized.Select((value, i) => value / Values[i]).ToList();

        Assert.NotEqual(0.0, normalized.Average(), 5);
        Assert.All(ratios, ratio => Assert.Equal(ratios[0], ratio, 5));
    }

    [Fact(DisplayName = "fixZero を外すと、自作の標準化と同じ値になる")]
    public void MatchesStandardizer()
    {
        var rows = Samples.Column("x", Values);
        var mine = Standardizer.Fit(rows).Transform(rows).Select(features => features.Value("x")).ToList();

        var library = MlNetNormalization.NormalizeMeanVariance(false, Values);

        Assert.Equal(mine.Count, library.Count);
        Assert.All(library.Select((value, i) => (value, i)), pair => Assert.Equal(mine[pair.i], pair.value, 5));
    }
}
