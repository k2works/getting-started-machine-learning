namespace MachineLearning.Chapter02;

using MachineLearning.Dataset;

/// <summary>アヤメのデータの前処理の結果を表示する。</summary>
public static class Program
{
    private const double TestSize = 0.3;
    private const int Seed = 0;

    public static void Run(TextWriter output)
    {
        ArgumentNullException.ThrowIfNull(output);
        var csvFile = Path.Combine(DataDir.Current(), "iris.csv");
        var table = Table.Load(csvFile);
        var split = Preprocessing.PrepareIris(csvFile, TestSize, Seed);
        output.WriteLine($"データ件数: {table.Rows.Count}");
        output.WriteLine(
            "欠損値の数: " + string.Join(", ", table.CountMissing().Select(pair => $"{pair.Key}={pair.Value}")));
        output.WriteLine($"訓練データ: {split.XTrain.Count} 件, テストデータ: {split.XTest.Count} 件");
        output.WriteLine("特徴量: " + string.Join(", ", split.XTrain[0].Columns));
    }
}
