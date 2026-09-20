namespace MachineLearning.Chapter01;

using System.Globalization;
using MachineLearning.Dataset;

/// <summary>実データでルールによる判定の正解率を表示する。</summary>
public static class Program
{
    public static void Run(TextWriter output)
    {
        ArgumentNullException.ThrowIfNull(output);
        var people = KinokoTakenoko.LoadPeople(Path.Combine(DataDir.Current(), "KvsT.csv"));
        var (features, labels) = KinokoTakenoko.SplitFeaturesAndLabels(people);
        var predictions = features.Select(KinokoTakenoko.PredictByRule).ToList();
        output.WriteLine($"データ件数: {people.Count}");
        output.WriteLine(
            "ルールによる判定の正解率: "
            + KinokoTakenoko.Accuracy(predictions, labels).ToString("F4", CultureInfo.InvariantCulture));
    }
}
