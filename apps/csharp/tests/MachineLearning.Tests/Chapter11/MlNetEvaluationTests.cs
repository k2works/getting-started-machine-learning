namespace MachineLearning.Tests.Chapter11;

using MachineLearning.Chapter07;
using MachineLearning.Chapter11;

public class MlNetEvaluationTests
{
    private static readonly string[] Actual = ["1", "1", "1", "0", "0", "0", "0", "1"];
    private static readonly string[] Predicted = ["1", "1", "0", "1", "0", "0", "0", "0"];

    [Fact(DisplayName = "2 値分類の適合率・再現率・F 値が ML.NET の評価と一致する")]
    public void BinaryMatches()
    {
        var cm = Metrics.Confusion("1", Actual, Predicted);

        var library = MlNetEvaluation.EvaluateBinary("1", Actual, Predicted);

        Assert.Equal(Metrics.Precision(cm), library.PositivePrecision, 12);
        Assert.Equal(Metrics.Recall(cm), library.PositiveRecall, 12);
        Assert.Equal(Metrics.F1Score(cm), library.F1Score, 12);
    }

    [Fact(DisplayName = "回帰の MAE・RMSE・MSE が ML.NET の評価と一致する")]
    public void RegressionMatches()
    {
        double[] actual = [1.0, 2.0, 4.0, 8.0];
        double[] predicted = [1.5, 2.0, 3.0, 10.0];

        var library = MlNetEvaluation.EvaluateRegression(actual, predicted);

        Assert.Equal(RegressionMetrics.MeanAbsoluteError(actual, predicted), library.MeanAbsoluteError, 9);
        Assert.Equal(RegressionMetrics.RootMeanSquaredError(actual, predicted), library.RootMeanSquaredError, 9);
        Assert.Equal(Metrics.MeanSquaredError(actual, predicted), library.MeanSquaredError, 9);
    }
}
