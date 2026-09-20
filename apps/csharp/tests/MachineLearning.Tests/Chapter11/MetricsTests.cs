namespace MachineLearning.Tests.Chapter11;

using MachineLearning.Chapter07;
using MachineLearning.Chapter11;

public class MetricsTests
{
    private static readonly ConfusionMatrix Cm = new(3, 1, 2, 4);

    [Fact(DisplayName = "正例と負例の予測の当たり外れを数える")]
    public void Counts()
    {
        Assert.Equal(new ConfusionMatrix(2, 1, 1, 1), Metrics.Confusion(1, [1, 1, 1, 0, 0], [1, 1, 0, 1, 0]));
    }

    [Fact(DisplayName = "どちらのラベルを正例とするかで数え方が変わる")]
    public void PositiveMatters()
    {
        Assert.Equal(
            new ConfusionMatrix(2, 2, 1, 1), Metrics.Confusion(0, [1, 1, 1, 0, 0, 0], [1, 0, 0, 0, 0, 1]));
    }

    [Fact(DisplayName = "正解と予測の件数が違えばエラーになる")]
    public void Mismatched()
    {
        Assert.Throws<ArgumentException>(() => Metrics.Confusion(1, [1, 0, 1], [1, 0]));
    }

    [Fact(DisplayName = "適合率は正例と予測したうち本当に正例だった割合")]
    public void Precision()
    {
        Assert.Equal(0.75, Metrics.Precision(Cm), 12);
    }

    [Fact(DisplayName = "再現率は本当の正例のうち正例と予測できた割合")]
    public void Recall()
    {
        Assert.Equal(0.6, Metrics.Recall(Cm), 12);
    }

    [Fact(DisplayName = "F 値は適合率と再現率の調和平均")]
    public void F1()
    {
        Assert.Equal(2.0 * 0.75 * 0.6 / (0.75 + 0.6), Metrics.F1Score(Cm), 12);
    }

    [Fact(DisplayName = "正例を 1 件も当てられなければ適合率と再現率と F 値は 0")]
    public void ZeroDenominator()
    {
        var missed = new ConfusionMatrix(0, 0, 3, 5);

        Assert.Equal(
            (0.0, 0.0, 0.0), (Metrics.Precision(missed), Metrics.Recall(missed), Metrics.F1Score(missed)));
    }

    [Fact(DisplayName = "MSE は誤差の 2 乗の平均")]
    public void Mse()
    {
        Assert.Equal((1.0 + 0.0 + 4.0) / 3, Metrics.MeanSquaredError([3.0, 5.0, 7.0], [2.0, 5.0, 9.0]), 12);
    }

    [Fact(DisplayName = "大きく外れた予測があると RMSE は MAE より大きく増える")]
    public void RmsePenalizesOutliers()
    {
        double[] actual = [3.0, 5.0, 8.0, 10.0];
        double[] predicted = [2.0, 5.0, 10.0, 30.0];

        Assert.Equal(Math.Sqrt(101.25), RegressionMetrics.RootMeanSquaredError(actual, predicted), 12);
        Assert.Equal(5.75, RegressionMetrics.MeanAbsoluteError(actual, predicted), 12);
    }

    [Fact(DisplayName = "混同行列の指標は正例を決めると評価関数になる")]
    public void ClassificationMetric()
    {
        Metric<string> recall = Metrics.ClassificationMetric(Metrics.Recall, "1");

        Assert.Equal(2.0 / 3, recall(["1", "1", "1", "0"], ["1", "1", "0", "0"]), 12);
    }
}
