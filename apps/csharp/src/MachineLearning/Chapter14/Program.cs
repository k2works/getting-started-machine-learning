namespace MachineLearning.Chapter14;

using System.Globalization;
using MachineLearning.Dataset;

/// <summary>卸売業者の顧客の支出額を K-means でクラスタリングし、エルボー法とクラスタごとの集計を表示する。</summary>
public static class Program
{
    /// <summary>初期中心を選ぶ乱数のシード。</summary>
    public const int Seed = 0;

    /// <summary>初期中心を選び直す回数。</summary>
    public const int NInit = 10;

    /// <summary>エルボー法で試すクラスタ数の上限。</summary>
    public const int MaxClusters = 10;

    /// <summary>集計するクラスタ数。</summary>
    public const int NClusters = 5;

    /// <summary>エルボー法で試すクラスタ数。</summary>
    public static IReadOnlyList<int> ClusterCounts => [.. Enumerable.Range(1, MaxClusters)];

    public static void Run(TextWriter output)
    {
        ArgumentNullException.ThrowIfNull(output);
        var rows = Spending.Load(Path.Combine(DataDir.Current(), "Wholesale.csv"));
        var points = Spending.ToStandardizedPoints(rows);
        output.WriteLine($"データ件数: {rows.Count}（支出額 {Spending.Columns.Length} 列）");
        output.WriteLine($"クラスタ数ごとの SSE（初期中心 {NInit} 通りの最小値）:");
        output.WriteLine("クラスタ数\t自作\tML.NET（k-means++）");
        foreach (var (n, sse) in KMeans.SseByClusterCount(NInit, Seed, ClusterCounts, points))
        {
            output.WriteLine($"{n}\t{Format(sse)}\t{Format(MlNetKMeans.BestSse(n, Seed, NInit, points))}");
        }

        var result = KMeans.FitWithRestarts(NInit, NClusters, Seed, points);
        output.WriteLine(string.Empty);
        output.WriteLine($"クラスタ数 {NClusters} のクラスタごとの件数と平均支出額:");
        output.WriteLine(string.Join("\t", ["クラスタ", "件数", .. Spending.Columns]));
        foreach (var summary in Spending.SummarizeClusters(result.Labels, rows))
        {
            output.WriteLine(string.Join(
                "\t",
                [
                    summary.Cluster.ToString(CultureInfo.InvariantCulture),
                    summary.Count.ToString(CultureInfo.InvariantCulture),
                    .. Spending.Columns.Select(column => summary.Means[column].ToString("F0", CultureInfo.InvariantCulture)),
                ]));
        }
    }

    private static string Format(double value) => value.ToString("F2", CultureInfo.InvariantCulture);
}
