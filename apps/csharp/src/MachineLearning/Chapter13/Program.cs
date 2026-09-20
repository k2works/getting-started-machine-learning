namespace MachineLearning.Chapter13;

using System.Globalization;
using MachineLearning.Chapter07;
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

        var mine = Pca.Transform(Pca.Fit(ComponentsToExplain, table.X), table.X);
        var library = MlNetPca.Project(ComponentsToExplain, false, 0, table.X);
        output.WriteLine(
            $"ML.NET の射影と一致した件数（第 {ComponentsToExplain} 主成分まで、符号を除く、許容誤差 {Tolerance}）: "
            + $"{CountAgreed(mine, library)}/{mine.RowCount}");
    }

    /// <summary>ML.NET の射影と一致したとみなす差の上限。ML.NET は float32 で計算する。</summary>
    private const double Tolerance = 1e-3;

    /// <summary>自作と ML.NET の主成分の座標が、符号を除いて一致した件数。</summary>
    private static int CountAgreed(Matrix mine, Matrix library) =>
        Enumerable.Range(0, mine.RowCount).Count(row =>
            Enumerable.Range(0, mine.ColumnCount).All(pc =>
                Math.Abs(Math.Abs(mine[row, pc]) - Math.Abs(library[row, pc])) <= Tolerance));

    private static string Format(double value, int digits) =>
        value.ToString($"F{digits}", CultureInfo.InvariantCulture);
}
