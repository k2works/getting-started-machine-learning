namespace MachineLearning.Chapter09;

using MachineLearning.Chapter02;

/// <summary>四分位範囲（IQR）による外れ値の検出。</summary>
public static class Outliers
{
    /// <summary>外れ値とみなす、四分位点から四分位範囲の何倍離れているか。</summary>
    public const double DefaultK = 1.5;

    private const double FirstQuartile = 0.25;
    private const double ThirdQuartile = 0.75;

    /// <summary>分位点を求める。位置が値の間にあれば前後の値から線形補間する（pandas の quantile の既定と同じ）。</summary>
    public static double Quantile(IReadOnlyList<double> values, double q)
    {
        ArgumentNullException.ThrowIfNull(values);
        var sorted = values.Order().ToList();
        var position = (sorted.Count - 1) * q;
        var lower = sorted[(int)Math.Floor(position)];
        var upper = sorted[(int)Math.Ceiling(position)];
        return lower + ((upper - lower) * (position - Math.Floor(position)));
    }

    /// <summary>第 1 四分位点から四分位範囲の k 倍より小さい値と、第 3 四分位点から k 倍より大きい値を外れ値とする。</summary>
    public static IReadOnlyList<bool> IqrOutliers(IReadOnlyList<double> values, double k = DefaultK)
    {
        ArgumentNullException.ThrowIfNull(values);
        var q1 = Quantile(values, FirstQuartile);
        var q3 = Quantile(values, ThirdQuartile);
        var iqr = q3 - q1;
        return [.. values.Select(value => value < q1 - (k * iqr) || value > q3 + (k * iqr))];
    }

    /// <summary>
    /// 訓練データから、正解の値が外れ値の行を取り除く。テストデータはそのまま残す。
    /// 本番のデータから外れ値を選んで捨てることはできないので、テストデータには手を付けない。
    /// </summary>
    public static TrainTestSplit<TX, double> RemoveTargetOutliers<TX>(TrainTestSplit<TX, double> split)
    {
        ArgumentNullException.ThrowIfNull(split);
        var outliers = IqrOutliers(split.TTrain);
        var kept = Enumerable.Range(0, outliers.Count).Where(i => !outliers[i]).ToList();
        return new TrainTestSplit<TX, double>(
            [.. kept.Select(i => split.XTrain[i])],
            split.XTest,
            [.. kept.Select(i => split.TTrain[i])],
            split.TTest);
    }
}
