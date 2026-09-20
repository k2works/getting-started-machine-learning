namespace MachineLearning.Chapter14;

using MachineLearning.Chapter02;
using MachineLearning.Chapter09;
using Point = System.Collections.Generic.IReadOnlyList<double>;

/// <summary>1 つのクラスタの件数と、列ごとの平均。</summary>
/// <param name="Cluster">クラスタ番号</param>
/// <param name="Count">件数</param>
/// <param name="Means">列ごとの平均（標準化する前の支出額）</param>
public sealed record ClusterSummary(int Cluster, int Count, IReadOnlyDictionary<string, double> Means);

/// <summary>卸売業者の顧客ごとの支出額（Wholesale.csv）の読み込みと、クラスタごとの集計。</summary>
public static class Spending
{
    /// <summary>支出額の列。Channel と Region は区分の番号で大小に意味が無いので使わない。</summary>
    public static readonly string[] Columns =
        ["Fresh", "Milk", "Grocery", "Frozen", "Detergents_Paper", "Delicassen"];

    /// <summary>顧客ごとの支出額を、支出額の 6 列だけの特徴量として読み込む。</summary>
    public static IReadOnlyList<Features> Load(string csvFile)
    {
        var rows = Table.Load(csvFile).Rows;
        return Preprocessing.FillMissing(rows, Columns, Preprocessing.ColumnMeans(rows, Columns));
    }

    /// <summary>第 9 章の Standardizer で列ごとに標準化し、1 件を 1 つの点にする。</summary>
    public static IReadOnlyList<Point> ToStandardizedPoints(IReadOnlyList<Features> rows)
    {
        ArgumentNullException.ThrowIfNull(rows);
        return [.. Standardizer.Fit(rows, Columns).Transform(rows).Select(features => features.Values)];
    }

    /// <summary>クラスタごとの件数と列ごとの平均を、件数の多い順に並べる。</summary>
    public static IReadOnlyList<ClusterSummary> SummarizeClusters(
        IReadOnlyList<int> labels, IReadOnlyList<Features> rows)
    {
        ArgumentNullException.ThrowIfNull(labels);
        ArgumentNullException.ThrowIfNull(rows);
        return [.. rows
            .Select((features, i) => (Cluster: labels[i], Features: features))
            .GroupBy(pair => pair.Cluster)
            .Select(group => new ClusterSummary(
                group.Key,
                group.Count(),
                Columns.ToDictionary(
                    column => column,
                    column => group.Average(pair => pair.Features.Value(column)),
                    StringComparer.Ordinal)))
            .OrderByDescending(summary => summary.Count)];
    }
}
