namespace MachineLearning.Chapter13;

using System.Globalization;
using MachineLearning.Dataset;

/// <summary>ボストンの住宅価格のデータを主成分分析し、寄与率と主成分への影響が大きい列を表示する。</summary>
public static class Program
{
    /// <summary>累積寄与率の目安。</summary>
    public const double Threshold = 0.8;

    /// <summary>主成分ごとに表示する列の数。</summary>
    public const int TopK = 3;

    /// <summary>意味を読む主成分の数。</summary>
    public const int ComponentsToExplain = 2;

    public static void Run(TextWriter output)
    {
        ArgumentNullException.ThrowIfNull(output);
        var table = BostonStandardized.Load(Path.Combine(DataDir.Current(), "Boston.csv"));
        var model = Pca.Fit(table.Columns.Count, table.X);
        var ratios = model.ExplainedVarianceRatio;
        var needed = Pca.ComponentsNeeded(Threshold, ratios);
        var shown = ratios.Take(needed).ToList();
        output.WriteLine($"データ件数: {table.X.RowCount}, 列数: {table.Columns.Count}");
        output.WriteLine(
            "寄与率: " + string.Join(", ", shown.Select((ratio, i) => $"PC{i + 1} {Format(ratio, 4)}")));
        output.WriteLine(
            $"累積寄与率が {Format(Threshold, 1)} に届く主成分の数: {needed}（累積寄与率 {Format(shown.Sum(), 4)}）");
        for (var i = 0; i < ComponentsToExplain; i++)
        {
            var loadings = Pca.TopLoadings(TopK, table.Columns, model.Components[i]);
            output.WriteLine(
                $"第 {i + 1} 主成分で影響の大きい列: "
                + string.Join(", ", loadings.Select(pair => $"{pair.Key} {Format(pair.Value, 3)}")));
        }
    }

    private static string Format(double value, int digits) =>
        value.ToString($"F{digits}", CultureInfo.InvariantCulture);
}
