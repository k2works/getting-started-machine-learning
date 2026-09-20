namespace MachineLearning.Tests.Chapter09;

using MachineLearning.Chapter02;
using MachineLearning.Chapter09;

public class StandardizerTests
{
    [Fact(DisplayName = "訓練データの平均と標準偏差で、指定した列だけを標準化する")]
    public void StandardizesSelectedColumns()
    {
        var train = Samples.Column("RM", 1.0, 2.0, 3.0);

        var standardized = Standardizer.Fit(train, ["RM"])
            .Transform(new Features(["RM", "LSTAT"], [4.0, 7.0]));

        Assert.Equal(2.0 / Math.Sqrt(2.0 / 3.0), standardized.Value("RM"), 12);
        Assert.Equal(7.0, standardized.Value("LSTAT"));
    }

    [Fact(DisplayName = "標準化した訓練データは平均 0・標準偏差 1 になる")]
    public void TrainBecomesZeroMean()
    {
        var train = Samples.Column("RM", 1.0, 2.0, 6.0);

        var again = Standardizer.Fit(Standardizer.Fit(train).Transform(train));

        Assert.Equal(0.0, again.Means["RM"], 12);
        Assert.Equal(1.0, again.Stds["RM"], 12);
    }

    [Fact(DisplayName = "標準偏差が 0 の列は 0 にする")]
    public void ConstantColumnBecomesZero()
    {
        var train = Samples.Column("CHAS", 1.0, 1.0);

        var standardized = Standardizer.Fit(train).Transform(train);

        Assert.Equal([0.0, 0.0], standardized.Select(features => features.Value("CHAS")));
    }

    [Fact(DisplayName = "件数が 0 ならエラーになる")]
    public void RejectsEmpty() =>
        Assert.Throws<ArgumentException>(() => Standardizer.Fit([]));
}
