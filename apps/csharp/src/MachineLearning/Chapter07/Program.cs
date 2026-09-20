namespace MachineLearning.Chapter07;

using System.Globalization;
using MachineLearning.Chapter02;
using MachineLearning.Dataset;

/// <summary>cinema.csv で線形回帰を学習し、係数とテストデータの評価、ML.NET との比較を表示する。</summary>
public static class Program
{
    private const double TestSize = 0.2;
    private const int Seed = 0;

    public static void Run(TextWriter output)
    {
        ArgumentNullException.ThrowIfNull(output);
        var csvFile = Path.Combine(DataDir.Current(), "cinema.csv");
        var table = Cinema.Load(csvFile);
        var split = Cinema.Prepare(csvFile, TestSize, Seed);
        var model = LinearRegression.Fit(split.XTrain, split.TTrain);
        var y = LinearRegression.Predict(model, split.XTest);
        var libraryY = MlNetRegression.TrainSdca(split.XTrain, split.TTrain)(split.XTest);

        output.WriteLine($"データ件数: {table.Rows.Count}");
        output.WriteLine($"外れ値を除いた件数: {Cinema.RemoveOutliers(table).Rows.Count}");
        output.WriteLine($"訓練データ: {split.XTrain.Count} 件, テストデータ: {split.XTest.Count} 件");
        output.WriteLine($"切片: {Format(model.Intercept, "F2")}");
        output.WriteLine($"係数: {FormatCoefficients(model.Coefficients)}");
        output.WriteLine("テストデータの評価: " + Evaluate(split.TTest, y));
        output.WriteLine("ML.NET(SDCA) の評価: " + Evaluate(split.TTest, libraryY));
    }

    private static string Format(double value, string format) => value.ToString(format, CultureInfo.InvariantCulture);

    private static string Evaluate(IReadOnlyList<double> t, IReadOnlyList<double> y) =>
        $"R2={Format(RegressionMetrics.R2Score(t, y), "F4")}, "
        + $"MAE={Format(RegressionMetrics.MeanAbsoluteError(t, y), "F2")}, "
        + $"RMSE={Format(RegressionMetrics.RootMeanSquaredError(t, y), "F2")}";

    private static string FormatCoefficients(Features coefficients) =>
        string.Join(
            ", ",
            coefficients.Columns.Select(column => $"{column}={Format(coefficients.Value(column), "F4")}"));
}
