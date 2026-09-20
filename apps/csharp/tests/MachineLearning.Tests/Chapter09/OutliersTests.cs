namespace MachineLearning.Tests.Chapter09;

using MachineLearning.Chapter02;
using MachineLearning.Chapter09;

public class OutliersTests
{
    private static readonly double[] Quantiles = [0.0, 0.25, 0.5, 1.0];

    [Fact(DisplayName = "分位点は並べた値の間を線形補間する")]
    public void InterpolatesQuantile()
    {
        IReadOnlyList<double> values = [4.0, 1.0, 3.0, 2.0];

        Assert.Equal(
            [1.0, 1.75, 2.5, 4.0],
            Quantiles.Select(q => Outliers.Quantile(values, q)));
    }

    [Fact(DisplayName = "四分位範囲の 1.5 倍より外側の値を外れ値とする")]
    public void FindsOutliers() =>
        Assert.Equal(
            [false, false, false, false, true],
            Outliers.IqrOutliers([1.0, 2.0, 3.0, 4.0, 100.0]));

    [Fact(DisplayName = "k を大きくすると外れ値でなくなる")]
    public void WiderKeepsAll() =>
        Assert.DoesNotContain(true, Outliers.IqrOutliers([1.0, 2.0, 3.0, 4.0, 100.0], 50.0));

    [Fact(DisplayName = "訓練データだけから正解の外れ値の行を除く")]
    public void RemovesFromTrainOnly()
    {
        var split = new TrainTestSplit<int, double>(
            [1, 2, 3, 4, 5], [6], [1.0, 2.0, 3.0, 4.0, 100.0], [1000.0]);

        var removed = Outliers.RemoveTargetOutliers(split);

        Assert.Equal([1, 2, 3, 4], removed.XTrain);
        Assert.Equal([1.0, 2.0, 3.0, 4.0], removed.TTrain);
        Assert.Equal([1000.0], removed.TTest);
    }
}
