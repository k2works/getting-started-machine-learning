namespace MachineLearning.Chapter11;

/// <summary>
/// 評価関数。正解と予測を受け取ってスコアを返す。
/// F# の型の省略形（type Metric&lt;'T&gt; = ...）に当たるものとして、名前付きのデリゲートで表す。
/// </summary>
/// <typeparam name="T">ラベルの型</typeparam>
/// <param name="actual">正解</param>
/// <param name="predicted">予測</param>
/// <returns>スコア</returns>
public delegate double Metric<T>(IReadOnlyList<T> actual, IReadOnlyList<T> predicted);

/// <summary>混同行列。正例についての当たり外れの件数。</summary>
/// <param name="TruePositive">正例と予測して当たった件数（TP）</param>
/// <param name="FalsePositive">正例と予測して外れた件数（FP）</param>
/// <param name="FalseNegative">負例と予測して外れた件数（FN）</param>
/// <param name="TrueNegative">負例と予測して当たった件数（TN）</param>
public readonly record struct ConfusionMatrix(
    int TruePositive, int FalsePositive, int FalseNegative, int TrueNegative);

/// <summary>分類と回帰の評価指標。</summary>
public static class Metrics
{
    /// <summary>正解と予測を「実際は正例か、正例と予測したか」の組にして、組ごとに数える。</summary>
    public static ConfusionMatrix Confusion<T>(T positive, IReadOnlyList<T> actual, IReadOnlyList<T> predicted)
    {
        ArgumentNullException.ThrowIfNull(actual);
        ArgumentNullException.ThrowIfNull(predicted);
        if (actual.Count != predicted.Count)
        {
            throw new ArgumentException($"正解 {actual.Count} 件と予測 {predicted.Count} 件の数が違います", nameof(predicted));
        }

        var comparer = EqualityComparer<T>.Default;
        var outcomes = actual
            .Zip(predicted, (a, p) => (Actual: comparer.Equals(a, positive), Predicted: comparer.Equals(p, positive)))
            .ToList();
        int Count(bool isActual, bool isPredicted) =>
            outcomes.Count(outcome => outcome.Actual == isActual && outcome.Predicted == isPredicted);

        return new ConfusionMatrix(Count(true, true), Count(false, true), Count(true, false), Count(false, false));
    }

    /// <summary>適合率。正例と予測したうち、本当に正例だった割合。</summary>
    public static double Precision(ConfusionMatrix cm) =>
        Ratio(cm.TruePositive, cm.TruePositive + cm.FalsePositive);

    /// <summary>再現率。本当の正例のうち、正例と予測できた割合。</summary>
    public static double Recall(ConfusionMatrix cm) =>
        Ratio(cm.TruePositive, cm.TruePositive + cm.FalseNegative);

    /// <summary>F 値。適合率と再現率の調和平均。</summary>
    public static double F1Score(ConfusionMatrix cm)
    {
        var precision = Precision(cm);
        var recall = Recall(cm);
        return Ratio(2.0 * precision * recall, precision + recall);
    }

    /// <summary>混同行列から求める指標と正例のラベルから、正解と予測から求める評価関数を作る。</summary>
    public static Metric<T> ClassificationMetric<T>(Func<ConfusionMatrix, double> score, T positive)
    {
        ArgumentNullException.ThrowIfNull(score);
        return (actual, predicted) => score(Confusion(positive, actual, predicted));
    }

    /// <summary>平均二乗誤差（MSE）。平方根をとった RMSE と MAE は第 7 章の RegressionMetrics にある。</summary>
    public static double MeanSquaredError(IReadOnlyList<double> actual, IReadOnlyList<double> predicted)
    {
        var rmse = Chapter07.RegressionMetrics.RootMeanSquaredError(actual, predicted);
        return rmse * rmse;
    }

    /// <summary>割り算。分母が 0 なら NaN にせず 0 を返す（scikit-learn の既定と同じ）。</summary>
    private static double Ratio(double numerator, double denominator) =>
        denominator == 0.0 ? 0.0 : numerator / denominator;
}
