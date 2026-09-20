namespace MachineLearning.Tests.Chapter07;

using MachineLearning.Chapter07;

public class RegressionMetricsTests
{
    [Fact(DisplayName = "完全に当たっていれば MAE は 0")]
    public void MaeExact()
    {
        Assert.Equal(0.0, RegressionMetrics.MeanAbsoluteError([3.0, 5.0], [3.0, 5.0]), 12);
    }

    [Fact(DisplayName = "MAE は誤差の絶対値の平均")]
    public void Mae()
    {
        Assert.Equal((1.0 + 0.0 + 2.0) / 3, RegressionMetrics.MeanAbsoluteError([3.0, 5.0, 7.0], [2.0, 5.0, 9.0]), 12);
    }

    [Fact(DisplayName = "RMSE は誤差の 2 乗の平均の平方根")]
    public void Rmse()
    {
        Assert.Equal(
            Math.Sqrt((1.0 + 0.0 + 4.0) / 3), RegressionMetrics.RootMeanSquaredError([3.0, 5.0, 7.0], [2.0, 5.0, 9.0]), 12);
    }

    [Fact(DisplayName = "R2 は 1 - 誤差の 2 乗の合計 / 実測値と平均値の差の 2 乗の合計")]
    public void R2()
    {
        // 実測値の平均は 5、平均との差の 2 乗の合計は 4 + 0 + 4 = 8、誤差の 2 乗の合計は 1 + 0 + 4 = 5
        Assert.Equal(1.0 - (5.0 / 8.0), RegressionMetrics.R2Score([3.0, 5.0, 7.0], [2.0, 5.0, 9.0]), 12);
    }

    [Fact(DisplayName = "常に平均値を予測すると R2 は 0")]
    public void R2Mean()
    {
        Assert.Equal(0.0, RegressionMetrics.R2Score([3.0, 5.0, 7.0], [5.0, 5.0, 5.0]), 12);
    }

    [Fact(DisplayName = "実測値と予測値の件数が違えば評価できない")]
    public void Mismatched()
    {
        Assert.Throws<ArgumentException>(() => RegressionMetrics.MeanAbsoluteError([3.0, 5.0], [3.0]));
    }
}
