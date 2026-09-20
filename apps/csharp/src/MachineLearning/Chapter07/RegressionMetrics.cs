namespace MachineLearning.Chapter07;

/// <summary>回帰の評価指標。第 11 章以降でも使う。</summary>
public static class RegressionMetrics
{
    /// <summary>平均絶対誤差。</summary>
    public static double MeanAbsoluteError(IReadOnlyList<double> t, IReadOnlyList<double> y) =>
        Residuals(t, y).Average(Math.Abs);

    /// <summary>二乗平均平方根誤差。</summary>
    public static double RootMeanSquaredError(IReadOnlyList<double> t, IReadOnlyList<double> y) =>
        Math.Sqrt(Residuals(t, y).Average(r => r * r));

    /// <summary>決定係数。1 - 誤差の 2 乗の合計 / 実測値と平均値の差の 2 乗の合計。</summary>
    public static double R2Score(IReadOnlyList<double> t, IReadOnlyList<double> y)
    {
        var residual = Residuals(t, y).Sum(r => r * r);
        var mean = t.Average();
        var total = t.Sum(value => (value - mean) * (value - mean));
        return 1.0 - (residual / total);
    }

    /// <summary>残差（実測値 - 予測値）。件数が違えば例外にする。</summary>
    private static IReadOnlyList<double> Residuals(IReadOnlyList<double> t, IReadOnlyList<double> y)
    {
        ArgumentNullException.ThrowIfNull(t);
        ArgumentNullException.ThrowIfNull(y);
        return t.Count != y.Count
            ? throw new ArgumentException($"実測値 {t.Count} 件と予測値 {y.Count} 件の数が違います", nameof(y))
            : [.. t.Zip(y).Select(pair => pair.First - pair.Second)];
    }
}
