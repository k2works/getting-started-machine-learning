namespace MachineLearning.Chapter10;

using System.Globalization;
using MachineLearning.Chapter02;
using MachineLearning.Dataset;

/// <summary>iris.csv でモデルごとの正解率と、ランダムフォレストの特徴量の重要度を表示する。</summary>
public static class Program
{
    private const double TestSize = 0.3;
    private const int Seed = 0;
    private const int NEstimators = 100;
    private const int ShallowDepth = 2;

    /// <summary>ML.NET の LbfgsMaximumEntropy の L1・L2 正則化の既定値。</summary>
    private const float MlNetRegularization = 1.0f;

    /// <summary>ML.NET の FastForest の既定値（葉ごとに最低 10 件）。</summary>
    private const int MlNetMinimumExampleCountPerLeaf = 10;

    private static readonly RandomForest.Settings ForestSettings =
        RandomForest.Settings.Default with { NEstimators = NEstimators, MaxFeatures = 2, Seed = Seed };

    /// <summary>表示名と分類器の組。</summary>
    private static readonly (string Name, IClassifier Classifier)[] Models =
    [
        ($"決定木（深さ {ShallowDepth}）", DecisionTreeClassifier.WithMaxDepth(ShallowDepth)),
        ("ロジスティック回帰", new LogisticRegression(LogisticSettings.Default)),
        ($"ランダムフォレスト（{NEstimators} 本）", new RandomForest(ForestSettings)),
        ($"ランダムフォレスト（{NEstimators} 本・深さ {ShallowDepth}）",
            new RandomForest(ForestSettings with { MaxDepth = ShallowDepth })),
        ("ML.NET LbfgsMaximumEntropy",
            MlNetClassifier.LbfgsMaximumEntropy(MlNetRegularization, MlNetRegularization)),
        ($"ML.NET FastForest（{NEstimators} 本）",
            MlNetClassifier.FastForest(NEstimators, MlNetMinimumExampleCountPerLeaf)),
    ];

    public static void Run(TextWriter output)
    {
        ArgumentNullException.ThrowIfNull(output);
        var split = Preprocessing.PrepareIris(Path.Combine(DataDir.Current(), "iris.csv"), TestSize, Seed);
        output.WriteLine("モデル\t訓練データ\tテストデータ");
        foreach (var (name, classifier) in Models)
        {
            var score = classifier.Evaluate(split);
            output.WriteLine($"{name}\t{Format(score.Train)}\t{Format(score.Test)}");
        }

        var forest = RandomForest.Learn(ForestSettings, split.XTrain, split.TTrain);
        output.WriteLine();
        output.WriteLine($"ランダムフォレスト（{NEstimators} 本）の特徴量の重要度:");
        var importances = FeatureImportance.ForestImportances(forest, split.XTrain, split.TTrain);
        foreach (var feature in split.XTrain[0].Columns)
        {
            output.WriteLine($"{feature}\t{Format(importances[feature])}");
        }
    }

    private static string Format(double value) => value.ToString("F4", CultureInfo.InvariantCulture);
}
