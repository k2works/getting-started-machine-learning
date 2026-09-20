namespace MachineLearning.Chapter12;

using System.Globalization;
using MachineLearning.Chapter07;
using MachineLearning.Dataset;

/// <summary>検証データで選んだ alpha の実験と、線形回帰・選んだリッジ回帰のテストデータの決定係数。</summary>
/// <param name="Experiments">alpha ごとの実験</param>
/// <param name="Best">検証データで選んだ実験</param>
/// <param name="LinearScore">線形回帰（alpha = 0）のテストデータの決定係数</param>
/// <param name="RidgeScore">選んだリッジ回帰のテストデータの決定係数</param>
public sealed record Comparison(
    IReadOnlyList<Experiment> Experiments, Experiment Best, double LinearScore, double RidgeScore);

/// <summary>Boston.csv で正則化の強さを変えた実験と、ラッソ回帰・ML.NET の結果、シードごとの比較を表示する。</summary>
public static class Program
{
    /// <summary>テストデータの割合。</summary>
    public const double TestSize = 0.3;

    /// <summary>残りのうち検証データにする割合。</summary>
    public const double ValidationSize = 0.3;

    /// <summary>分け方を決める乱数のシード。</summary>
    public const int Seed = 0;

    /// <summary>ラッソ回帰で試す正則化の強さ。</summary>
    public const double LassoAlpha = 1.0;

    /// <summary>比べる正則化の強さ。</summary>
    public static readonly double[] Alphas = [0.0, 0.1, 1.0, 10.0, 100.0];

    /// <summary>分け方の違いを見るために比べるシード。</summary>
    public static readonly int[] SeedsToCompare = [0, 1, 2, 3, 4];

    /// <summary>検証データで alpha を選び、線形回帰と選んだリッジ回帰をテストデータで 1 回だけ評価する。</summary>
    public static Comparison CompareOnTestData(BostonDataset dataset)
    {
        ArgumentNullException.ThrowIfNull(dataset);
        var experiments = Regularization.RunRidgeExperiments(
            Alphas, dataset.XTrain, dataset.TTrain, dataset.XValid, dataset.TValid);
        var best = Regularization.BestExperiment(experiments);
        double Score(double alpha) => RegressionMetrics.R2Score(
            dataset.TTest,
            Regularization.Predict(Regularization.FitRidge(alpha, dataset.XTrain, dataset.TTrain), dataset.XTest));

        return new Comparison(experiments, best, Score(0.0), Score(best.Alpha));
    }

    public static void Run(TextWriter output)
    {
        ArgumentNullException.ThrowIfNull(output);
        var csvFile = Path.Combine(DataDir.Current(), "Boston.csv");
        var (allX, allT) = Boston.Load(csvFile);
        var (keptX, _) = Boston.RemoveOutliers(allX, allT, Boston.OutlierThreshold);
        var dataset = Boston.Prepare(csvFile, TestSize, ValidationSize, Seed);
        output.WriteLine($"データ件数: {keptX.Count}（外れ値 {allX.Count - keptX.Count} 件を除外）");
        output.WriteLine(
            $"訓練データ: {dataset.TTrain.Count} 件, 検証データ: {dataset.TValid.Count} 件, "
            + $"テストデータ: {dataset.TTest.Count} 件");
        output.WriteLine("特徴量: " + string.Join(", ", dataset.FeatureNames));

        var comparison = CompareOnTestData(dataset);
        output.WriteLine("alpha\t訓練 R²\t検証 R²\t係数の絶対値の合計");
        foreach (var experiment in comparison.Experiments)
        {
            output.WriteLine(
                $"{Format(experiment.Alpha, "G")}\t{Format(experiment.TrainScore, "F4")}\t"
                + $"{Format(experiment.ValidationScore, "F4")}\t{Format(experiment.CoefficientAbsSum, "F3")}");
        }

        output.WriteLine($"検証データで選んだ alpha: {Format(comparison.Best.Alpha, "G")}");
        output.WriteLine(
            $"テストデータの決定係数: 線形回帰 {Format(comparison.LinearScore, "F4")}, "
            + $"リッジ回帰 {Format(comparison.RidgeScore, "F4")}");

        var mlNet = RegressionMetrics.R2Score(
            dataset.TTest,
            Regularization.Predict(
                MlNetRegularization.FitRidgeWithMlNet(comparison.Best.Alpha, dataset.XTrain, dataset.TTrain),
                dataset.XTest));
        output.WriteLine($"ML.NET（SDCA）のリッジ回帰のテストデータの決定係数: {Format(mlNet, "F4")}");

        var lasso = Regularization.FitLasso(LassoAlpha, dataset.XTrain, dataset.TTrain);
        var zeros = Regularization.ZeroCoefficientNames(lasso.Coefficients, dataset.FeatureNames);
        output.WriteLine(
            $"ラッソ回帰（alpha={Format(LassoAlpha, "G")}）で係数が 0 になった特徴量: " + string.Join(", ", zeros));

        output.WriteLine(string.Empty);
        output.WriteLine("シード\t選んだ alpha\t線形回帰\tリッジ回帰");
        foreach (var seed in SeedsToCompare)
        {
            var comparisonForSeed = CompareOnTestData(Boston.Prepare(csvFile, TestSize, ValidationSize, seed));
            output.WriteLine(
                $"{seed}\t{Format(comparisonForSeed.Best.Alpha, "G")}\t"
                + $"{Format(comparisonForSeed.LinearScore, "F4")}\t{Format(comparisonForSeed.RidgeScore, "F4")}");
        }
    }

    private static string Format(double value, string format) => value.ToString(format, CultureInfo.InvariantCulture);
}
