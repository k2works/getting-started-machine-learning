namespace MachineLearning.Chapter11;

using MachineLearning.Chapter02;
using MachineLearning.Chapter07;

/// <summary>評価関数ごとに K 分割交差検証の平均を求める実験。</summary>
public static class Experiments
{
    /// <summary>交差検証の分割数。</summary>
    public const int NSplits = 5;

    /// <summary>分け方を決める乱数のシード。</summary>
    public const int Seed = 0;

    /// <summary>決定木の深さの上限。</summary>
    public const int TreeDepth = 2;

    /// <summary>生存を正例とする。</summary>
    public const string Survived = "1";

    /// <summary>Survived を評価する指標（名前と評価関数の組）。</summary>
    public static IReadOnlyList<KeyValuePair<string, Metric<string>>> SurvivedMetrics =>
    [
        KeyValuePair.Create<string, Metric<string>>(
            "正解率", (actual, predicted) => Chapter01.KinokoTakenoko.Accuracy(predicted, actual)),
        KeyValuePair.Create("適合率", Metrics.ClassificationMetric(Metrics.Precision, Survived)),
        KeyValuePair.Create("再現率", Metrics.ClassificationMetric(Metrics.Recall, Survived)),
        KeyValuePair.Create("F値", Metrics.ClassificationMetric(Metrics.F1Score, Survived)),
    ];

    /// <summary>cinema を評価する指標（名前と評価関数の組）。</summary>
    public static IReadOnlyList<KeyValuePair<string, Metric<double>>> CinemaMetrics =>
    [
        KeyValuePair.Create<string, Metric<double>>("RMSE", RegressionMetrics.RootMeanSquaredError),
        KeyValuePair.Create<string, Metric<double>>("MAE", RegressionMetrics.MeanAbsoluteError),
    ];

    /// <summary>評価関数ごとに、K 分割交差検証のスコアの平均を求めて、名前と組にする。</summary>
    public static IReadOnlyList<KeyValuePair<string, double>> Evaluate<T>(
        int nSplits,
        int seed,
        Model<T> model,
        IReadOnlyList<KeyValuePair<string, Metric<T>>> metrics,
        IReadOnlyList<Features> x,
        IReadOnlyList<T> t)
    {
        ArgumentNullException.ThrowIfNull(metrics);
        ArgumentNullException.ThrowIfNull(x);
        var folds = CrossValidation.KFold(nSplits, seed, x.Count);
        return [.. metrics.Select(metric => KeyValuePair.Create(
            metric.Key,
            CrossValidation.CrossValidate(model, metric.Value, folds, x, t).Average()))];
    }

    /// <summary>Survived を深さ 2 の決定木で評価する。</summary>
    public static IReadOnlyList<KeyValuePair<string, double>> EvaluateSurvived(string csvFile)
    {
        var (x, t) = Datasets.PrepareSurvived(csvFile);
        return Evaluate(NSplits, Seed, Models.DecisionTree(TreeDepth), SurvivedMetrics, x, t);
    }

    /// <summary>cinema を線形回帰で評価する。</summary>
    public static IReadOnlyList<KeyValuePair<string, double>> EvaluateCinema(string csvFile)
    {
        var (x, t) = Datasets.PrepareCinema(csvFile);
        return Evaluate(NSplits, Seed, Models.LinearRegression, CinemaMetrics, x, t);
    }
}
