namespace MachineLearning.Chapter11;

using System.Globalization;
using MachineLearning.Dataset;

/// <summary>Survived（決定木）と cinema（線形回帰）を、K 分割交差検証の平均で評価して表示する。</summary>
public static class Program
{
    public static void Run(TextWriter output)
    {
        ArgumentNullException.ThrowIfNull(output);
        var dataDir = DataDir.Current();

        output.WriteLine($"Survived（決定木、{Experiments.NSplits} 分割交差検証の平均）");
        foreach (var (name, score) in Experiments.EvaluateSurvived(Path.Combine(dataDir, "Survived.csv")))
        {
            output.WriteLine($"  {name}: {Format(score, "F4")}");
        }

        output.WriteLine($"cinema（線形回帰、{Experiments.NSplits} 分割交差検証の平均）");
        foreach (var (name, score) in Experiments.EvaluateCinema(Path.Combine(dataDir, "cinema.csv")))
        {
            output.WriteLine($"  {name}: {Format(score, "F2")}");
        }
    }

    private static string Format(double value, string format) => value.ToString(format, CultureInfo.InvariantCulture);
}
