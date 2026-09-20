namespace MachineLearning.Chapter03;

using System.Globalization;
using MachineLearning.Chapter01;
using MachineLearning.Chapter02;
using Features = MachineLearning.Chapter02.Features;
using MachineLearning.Dataset;

/// <summary>深さごとの正解率、ML.NET との予測の一致数、深さ 2 の決定木を表示する。</summary>
public static class Program
{
    private const double TestSize = 0.3;
    private const int Seed = 0;
    private const int TreeDepthToShow = 2;

    private static readonly int[] MaxDepths = [1, 2, 3, 4, 5];

    public static void Run(TextWriter output)
    {
        ArgumentNullException.ThrowIfNull(output);
        var split = Preprocessing.PrepareIris(Path.Combine(DataDir.Current(), "iris.csv"), TestSize, Seed);
        output.WriteLine("深さ\t訓練データ\tテストデータ\tML.NET と一致");
        foreach (var maxDepth in MaxDepths)
        {
            WriteRow(output, maxDepth.ToString(CultureInfo.InvariantCulture), DecisionTree.WithMaxDepth(maxDepth), LeafLimit(maxDepth, split), split);
        }

        WriteRow(output, "制限なし", DecisionTree.Unlimited(), split.XTrain.Count, split);

        var shallow = DecisionTree.WithMaxDepth(TreeDepthToShow).Fit(split.XTrain, split.TTrain);
        output.WriteLine();
        output.WriteLine($"深さ {TreeDepthToShow} の決定木:");
        output.WriteLine(DecisionTrees.Format(shallow.Tree!));
    }

    /// <summary>深さ d の決定木の葉の数の上限（2 の d 乗）。</summary>
    private static int LeafLimit(int maxDepth, TrainTestSplit<Features, string> split) =>
        Math.Min((int)Math.Pow(2, maxDepth), split.XTrain.Count);

    private static void WriteRow(
        TextWriter output,
        string label,
        DecisionTree model,
        int numberOfLeaves,
        TrainTestSplit<Features, string> split)
    {
        model.Fit(split.XTrain, split.TTrain);
        var train = KinokoTakenoko.Accuracy(model.Predict(split.XTrain), split.TTrain);
        var test = KinokoTakenoko.Accuracy(model.Predict(split.XTest), split.TTest);
        var mlNet = MlNetAdapter.TrainFastTree(numberOfLeaves, split.XTrain, split.TTrain)(split.XTest);
        var agreed = model.Predict(split.XTest).Zip(mlNet).Count(pair => string.Equals(pair.First, pair.Second, StringComparison.Ordinal));
        output.WriteLine(
            $"{label}\t{Format(train)}\t{Format(test)}\t{agreed}/{split.XTest.Count}");
    }

    private static string Format(double value) => value.ToString("F4", CultureInfo.InvariantCulture);
}
