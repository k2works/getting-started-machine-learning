namespace MachineLearning.Tests.Chapter09;

using MachineLearning.Chapter02;
using MachineLearning.Chapter09;

public class LinearModelTests
{
    [Fact(DisplayName = "直線に乗る点から切片と係数を求める")]
    public void FitsLine()
    {
        var model = LinearModel.Fit(Samples.Column("x", 1.0, 2.0, 3.0), [3.0, 5.0, 7.0]);

        Assert.Equal(1.0, model.Intercept, 9);
        Assert.Equal(2.0, model.Weights[0], 9);
    }

    [Fact(DisplayName = "2 つの列でも正規方程式を解ける")]
    public void FitsTwoColumns()
    {
        IReadOnlyList<Features> x =
        [
            new Features(["a", "b"], [0.0, 0.0]),
            new Features(["a", "b"], [1.0, 0.0]),
            new Features(["a", "b"], [0.0, 1.0]),
            new Features(["a", "b"], [1.0, 1.0]),
        ];

        var model = LinearModel.Fit(x, [1.0, 3.0, 4.0, 6.0]);

        Assert.Equal([2.0, 3.0], model.Weights.Select(weight => Math.Round(weight, 9)));
        Assert.Equal(1.0, model.Intercept, 9);
    }

    [Fact(DisplayName = "同じ値の列が 2 つあると正規方程式を解けない")]
    public void RejectsDependentColumns()
    {
        IReadOnlyList<Features> x =
        [
            new Features(["a", "b"], [1.0, 1.0]),
            new Features(["a", "b"], [2.0, 2.0]),
            new Features(["a", "b"], [3.0, 3.0]),
        ];

        Assert.Throws<ArgumentException>(() => LinearModel.Fit(x, [1.0, 2.0, 3.0]));
    }

    [Fact(DisplayName = "予測が正解と一致すれば決定係数は 1 になる")]
    public void PerfectRSquared() =>
        Assert.Equal(1.0, LinearModel.RSquared([1.0, 2.0, 3.0], [1.0, 2.0, 3.0]));

    [Fact(DisplayName = "平均を予測すると決定係数は 0 になる")]
    public void MeanRSquared() =>
        Assert.Equal(0.0, LinearModel.RSquared([1.0, 2.0, 3.0], [2.0, 2.0, 2.0]));

    [Fact(DisplayName = "特徴量と正解の件数が違えばエラーになる")]
    public void SizeMismatch() =>
        Assert.Throws<ArgumentException>(() => LinearModel.Fit(Samples.Column("x", 1.0), [1.0, 2.0]));
}
