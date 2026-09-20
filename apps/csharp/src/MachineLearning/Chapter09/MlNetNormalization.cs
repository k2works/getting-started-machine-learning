namespace MachineLearning.Chapter09;

using Microsoft.ML;

/// <summary>ML.NET に渡す 1 行（1 列）。ML.NET は書き換えられるプロパティを持つクラスを求める。</summary>
public sealed class ValueRow
{
    public float Value { get; set; }
}

/// <summary>ML.NET の NormalizeMeanVariance で 1 列の値を正規化し、自作の標準化と突き合わせる。</summary>
public static class MlNetNormalization
{
    /// <summary>fixZero が true（ML.NET の既定）なら平均を引かず、元の 0 を 0 のまま保つ。</summary>
    public static IReadOnlyList<double> NormalizeMeanVariance(bool fixZero, IReadOnlyList<double> values)
    {
        ArgumentNullException.ThrowIfNull(values);
        var context = new MLContext(seed: 0);
        var data = context.Data.LoadFromEnumerable(
            values.Select(value => new ValueRow { Value = (float)value }).ToList());
        var transformed = context.Transforms
            .NormalizeMeanVariance("Value", fixZero: fixZero)
            .Fit(data)
            .Transform(data);
        return [.. context.Data
            .CreateEnumerable<ValueRow>(transformed, reuseRowObject: false)
            .Select(row => (double)row.Value)];
    }
}
