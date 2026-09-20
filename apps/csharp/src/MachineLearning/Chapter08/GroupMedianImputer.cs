namespace MachineLearning.Chapter08;

using System.Globalization;
using MachineLearning.Chapter02;

/// <summary>数値の列の欠損値を、同じグループ（By の列の値の組）の中央値で補完する前処理。</summary>
public sealed record GroupMedianImputer(string Column, IReadOnlyList<string> By) : ITransformer
{
    /// <summary>中央値。件数が偶数なら中央の 2 つの平均。</summary>
    public static double Median(IReadOnlyList<double> values)
    {
        ArgumentNullException.ThrowIfNull(values);
        var sorted = values.Order().ToList();
        var middle = sorted.Count / 2;
        return sorted.Count % 2 == 1 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
    }

    public IFittedTransformer Fit(Table x)
    {
        ArgumentNullException.ThrowIfNull(x);
        var known = x.Rows.Where(row => !row.IsMissing(this.Column)).ToList();
        var medians = known
            .GroupBy(row => GroupOf(row, this.By), GroupComparer.Instance)
            .Select(group => new GroupMedian(
                group.Key,
                Median([.. group.Select(row => row.Number(this.Column)!.Value)])))
            .ToList();
        var overall = Median([.. known.Select(row => row.Number(this.Column)!.Value)]);
        return new Fitted(this.Column, this.By, medians, overall);
    }

    /// <summary>行のグループ。By の列の値を並べたもの。</summary>
    private static IReadOnlyList<string> GroupOf(Row row, IReadOnlyList<string> by) =>
        [.. by.Select(row.Text)];

    /// <summary>グループと、そのグループの中央値。</summary>
    public sealed record GroupMedian(IReadOnlyList<string> Group, double Median);

    /// <summary>Fit で求めたグループごとの中央値と全体の中央値を持ち、欠損値を補完する。</summary>
    public sealed record Fitted(
        string Column,
        IReadOnlyList<string> By,
        IReadOnlyList<GroupMedian> Medians,
        double OverallMedian) : IFittedTransformer
    {
        public Table Transform(Table x)
        {
            ArgumentNullException.ThrowIfNull(x);
            return new Table(x.Columns, [.. x.Rows.Select(row => this.Fill(x.Columns, row))]);
        }

        private Row Fill(IReadOnlyList<string> columns, Row row)
        {
            if (!row.IsMissing(this.Column))
            {
                return row;
            }

            var group = GroupOf(row, this.By);
            var median = this.Medians
                .FirstOrDefault(entry => GroupComparer.Instance.Equals(entry.Group, group))?.Median
                ?? this.OverallMedian;
            return Rows.With(columns, row, this.Column, median.ToString(CultureInfo.InvariantCulture));
        }
    }

    /// <summary>グループ（列の値の並び）を中身で比べる。</summary>
    private sealed class GroupComparer : IEqualityComparer<IReadOnlyList<string>>
    {
        public static GroupComparer Instance { get; } = new();

        public bool Equals(IReadOnlyList<string>? x, IReadOnlyList<string>? y) =>
            x is not null && y is not null && x.SequenceEqual(y, StringComparer.Ordinal);

        public int GetHashCode(IReadOnlyList<string> obj)
        {
            var hash = default(HashCode);
            foreach (var value in obj)
            {
                hash.Add(value, StringComparer.Ordinal);
            }

            return hash.ToHashCode();
        }
    }
}
