namespace MachineLearning.Chapter11;

using Microsoft.ML;
using Microsoft.ML.Data;

/// <summary>2 値分類の評価に渡す 1 行。ML.NET は正解（Label）・予測（PredictedLabel）・得点（Score）の列を読む。</summary>
public sealed class BinaryOutcome
{
    public bool Label { get; set; }

    public bool PredictedLabel { get; set; }

    public float Score { get; set; }
}

/// <summary>回帰の評価に渡す 1 行。予測は Score の列に入れる。</summary>
public sealed class RegressionOutcome
{
    public float Label { get; set; }

    public float Score { get; set; }
}

/// <summary>自作の評価指標を ML.NET の評価と突き合わせる。</summary>
public static class MlNetEvaluation
{
    /// <summary>
    /// 正解と予測を ML.NET の 2 値分類の評価に渡す。
    /// 自作の決定木はラベルしか返さないので、確率を使わない評価（NonCalibrated）にする。
    /// </summary>
    public static BinaryClassificationMetrics EvaluateBinary<T>(
        T positive, IReadOnlyList<T> actual, IReadOnlyList<T> predicted)
    {
        ArgumentNullException.ThrowIfNull(actual);
        ArgumentNullException.ThrowIfNull(predicted);
        var comparer = EqualityComparer<T>.Default;
        var context = new MLContext(seed: 0);
        var rows = actual.Zip(predicted, (a, p) => new BinaryOutcome
        {
            Label = comparer.Equals(a, positive),
            PredictedLabel = comparer.Equals(p, positive),
            Score = comparer.Equals(p, positive) ? 1f : -1f,
        }).ToList();
        return context.BinaryClassification.EvaluateNonCalibrated(context.Data.LoadFromEnumerable(rows));
    }

    /// <summary>正解と予測を ML.NET の回帰の評価に渡す。</summary>
    public static Microsoft.ML.Data.RegressionMetrics EvaluateRegression(
        IReadOnlyList<double> actual, IReadOnlyList<double> predicted)
    {
        ArgumentNullException.ThrowIfNull(actual);
        ArgumentNullException.ThrowIfNull(predicted);
        var context = new MLContext(seed: 0);
        var rows = actual.Zip(predicted, (a, p) => new RegressionOutcome
        {
            Label = (float)a,
            Score = (float)p,
        }).ToList();
        return context.Regression.Evaluate(context.Data.LoadFromEnumerable(rows));
    }
}
