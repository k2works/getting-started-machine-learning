namespace MachineLearning.Chapter09;

using System.Globalization;
using MachineLearning.Chapter02;
using MachineLearning.Dataset;

/// <summary>ボストンの住宅価格で特徴量エンジニアリングを試し、自転車の利用者数に天気を結合して集計する。</summary>
public static class Program
{
    /// <summary>多項式特徴量を作る元の列。</summary>
    public static readonly string[] Columns = ["RM", "LSTAT", "PTRATIO"];

    /// <summary>2 乗の項。</summary>
    public static readonly string[] Squares = ["RM^2", "LSTAT^2", "PTRATIO^2"];

    private const double TestSize = 0.3;
    private const int Seed = 0;

    // 浮動小数点の誤差で -0.00 と表示されないように、ごく小さい値は 0 とみなす。
    // ML.NET は float32（単精度）で計算するので、誤差は 1e-9 より大きくなる
    private const double ZeroTolerance = 1e-6;

    private const string TargetColumn = "RM";

    /// <summary>比べる特徴量の組（名前と、使う項）。</summary>
    public static IReadOnlyList<KeyValuePair<string, IReadOnlyList<string>>> FeatureSets()
    {
        IReadOnlyList<string> interactions =
            [.. PolynomialFeatures.PairsWithReplacement(Columns).Select(term => term.Name)];
        return
        [
            KeyValuePair.Create("元の特徴量", (IReadOnlyList<string>)Columns),
            KeyValuePair.Create("2 乗の項を追加", (IReadOnlyList<string>)[.. Columns, .. Squares]),
            KeyValuePair.Create("交互作用の項も追加", (IReadOnlyList<string>)[.. Columns, .. interactions]),
        ];
    }

    public static void Run(TextWriter output)
    {
        ArgumentNullException.ThrowIfNull(output);
        var dataDir = DataDir.Current();
        var split = Boston.Prepare(Path.Combine(dataDir, "Boston.csv"), TestSize, Seed);
        output.WriteLine($"訓練データ: {split.XTrain.Count} 件, テストデータ: {split.XTest.Count} 件");
        output.WriteLine("特徴量の列: " + string.Join(", ", split.XTrain[0].Columns));

        // 標準化した訓練データを、もう一度 Fit して平均 0・標準偏差 1 になったことを確かめる
        var mine = Standardizer.Fit(Standardizer.Fit(split.XTrain).Transform(split.XTrain), [TargetColumn]);
        output.WriteLine(
            $"標準化した訓練データの {TargetColumn}: "
            + $"平均 {Format(mine.Means[TargetColumn], 2)}, 標準偏差 {Format(mine.Stds[TargetColumn], 2)}");

        var normalized = MlNetNormalization.NormalizeMeanVariance(
            false, [.. split.XTrain.Select(features => features.Value(TargetColumn))]);
        var library = Standardizer.Fit([.. normalized.Select(value => new Features([TargetColumn], [value]))]);
        output.WriteLine(
            $"ML.NET で正規化した訓練データの {TargetColumn}: "
            + $"平均 {Format(library.Means[TargetColumn], 2)}, 標準偏差 {Format(library.Stds[TargetColumn], 2)}");

        output.WriteLine("決定係数:");
        foreach (var (name, terms) in FeatureSets())
        {
            output.WriteLine($"  {name}（{terms.Count} 列）: {Format(Boston.ScoreFeatureSet(split, Columns, terms))}");
        }

        var outliers = Outliers.IqrOutliers(split.TTrain).Count(outlier => outlier);
        output.WriteLine($"訓練データの PRICE の外れ値: {outliers} 件");
        var removed = Boston.ScoreFeatureSet(
            Outliers.RemoveTargetOutliers(split), Columns, [.. Columns, .. Squares]);
        output.WriteLine($"  外れ値を除いて 2 乗の項を追加: {Format(removed)}");

        var joined = BikeWeather.JoinWeather(
            BikeWeather.LoadBike(Path.Combine(dataDir, "bike.tsv")),
            BikeWeather.LoadWeather(Path.Combine(dataDir, "weather.csv")));
        output.WriteLine(
            "天気ごとの平均利用者数: "
            + string.Join(
                ", ",
                BikeWeather.MeanCountByWeather(joined).Select(pair => $"{pair.Key}={Format(pair.Value, 1)}")));
    }

    private static string Format(double value, int digits) =>
        (Math.Abs(value) < ZeroTolerance ? 0.0 : value).ToString($"F{digits}", CultureInfo.InvariantCulture);

    private static string Format(Scores scores) =>
        $"訓練 {Format(scores.Train, 4)}, テスト {Format(scores.Test, 4)}";
}
