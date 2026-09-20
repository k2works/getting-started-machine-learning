namespace MachineLearning.Chapter09;

using MachineLearning.Chapter02;

/// <summary>
/// 列ごとの平均と標準偏差（件数で割る母標準偏差）で、平均 0・標準偏差 1 にそろえる。
/// 訓練データで <see cref="Fit(IReadOnlyList{Features})"/> し、同じ平均と標準偏差で
/// 訓練データとテストデータの両方を <see cref="Transform(IReadOnlyList{Features})"/> する。
/// 第 14 章（K-means）もこの型で特徴量をそろえる。
/// </summary>
public sealed class Standardizer
{
    private readonly Dictionary<string, double> means;
    private readonly Dictionary<string, double> stds;

    private Standardizer(Dictionary<string, double> means, Dictionary<string, double> stds)
    {
        this.means = means;
        this.stds = stds;
    }

    /// <summary>列ごとの平均。</summary>
    public IReadOnlyDictionary<string, double> Means => this.means;

    /// <summary>列ごとの標準偏差。すべて同じ値の列は 1 にして、標準化した値が 0 になるようにする。</summary>
    public IReadOnlyDictionary<string, double> Stds => this.stds;

    /// <summary>特徴量のすべての列について、平均と標準偏差を求める。</summary>
    public static Standardizer Fit(IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(x);
        return Fit(x, x.Count > 0 ? x[0].Columns : throw new ArgumentException("特徴量が 1 件もありません", nameof(x)));
    }

    /// <summary>指定した列だけについて、平均と標準偏差を求める。ほかの列は標準化しない。</summary>
    public static Standardizer Fit(IReadOnlyList<Features> x, IReadOnlyList<string> columns)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(columns);
        if (x.Count == 0)
        {
            throw new ArgumentException("特徴量が 1 件もありません", nameof(x));
        }

        var means = new Dictionary<string, double>(StringComparer.Ordinal);
        var stds = new Dictionary<string, double>(StringComparer.Ordinal);
        foreach (var column in columns)
        {
            var values = x.Select(features => features.Value(column)).ToList();
            var mean = values.Average();
            var std = Math.Sqrt(values.Average(value => (value - mean) * (value - mean)));
            means[column] = mean;
            stds[column] = std == 0 ? 1.0 : std;
        }

        return new Standardizer(means, stds);
    }

    /// <summary>1 件の特徴量を標準化する。平均と標準偏差を持たない列はそのまま残す。</summary>
    public Features Transform(Features features)
    {
        ArgumentNullException.ThrowIfNull(features);
        return new Features(
            features.Columns,
            [.. features.Columns.Select((column, i) => this.means.TryGetValue(column, out var mean)
                ? (features.Values[i] - mean) / this.stds[column]
                : features.Values[i])]);
    }

    /// <summary>特徴量のリストを標準化する。</summary>
    public IReadOnlyList<Features> Transform(IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(x);
        return [.. x.Select(this.Transform)];
    }
}
