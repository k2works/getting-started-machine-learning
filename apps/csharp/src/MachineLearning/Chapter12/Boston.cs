namespace MachineLearning.Chapter12;

using MachineLearning.Chapter02;
using MachineLearning.Chapter07;

/// <summary>訓練データ・検証データ・テストデータと、特徴量の名前。</summary>
/// <param name="XTrain">訓練データの特徴量</param>
/// <param name="TTrain">訓練データの正解</param>
/// <param name="XValid">検証データの特徴量</param>
/// <param name="TValid">検証データの正解</param>
/// <param name="XTest">テストデータの特徴量</param>
/// <param name="TTest">テストデータの正解</param>
/// <param name="FeatureNames">特徴量の名前（元の列と 2 次の項）</param>
public sealed record BostonDataset(
    Matrix XTrain,
    IReadOnlyList<double> TTrain,
    Matrix XValid,
    IReadOnlyList<double> TValid,
    Matrix XTest,
    IReadOnlyList<double> TTest,
    IReadOnlyList<string> FeatureNames);

/// <summary>ボストンの住宅価格（Boston.csv）を、正則化の実験に使える形に整える。</summary>
public static class Boston
{
    /// <summary>この章で使う特徴量の列。</summary>
    public static readonly string[] FeatureNames = ["RM", "PTRATIO", "LSTAT"];

    /// <summary>予測したい値の列。</summary>
    public const string Target = "PRICE";

    /// <summary>z スコアの絶対値がこの値を超える値を持つ行を外れ値とする。</summary>
    public const double OutlierThreshold = 3.0;

    /// <summary>Boston.csv のうち、この章で使う列だけを読み込む。</summary>
    public static (IReadOnlyList<Features> X, IReadOnlyList<double> T) Load(string csvFile)
    {
        var table = Table.Load(csvFile);
        var x = table.Rows
            .Select(row => new Features(FeatureNames, [.. FeatureNames.Select(column => Value(row, column))]))
            .ToList();
        return (x, [.. table.Rows.Select(row => Value(row, Target))]);
    }

    /// <summary>
    /// 列ごとの z スコア（標本標準偏差で割る）の絶対値が threshold を超える値を、1 つでも持つ行を除く。
    /// 正解の列も判定の対象にする。
    /// </summary>
    public static (IReadOnlyList<Features> X, IReadOnlyList<double> T) RemoveOutliers(
        IReadOnlyList<Features> x, IReadOnlyList<double> t, double threshold)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        var columns = x[0].Columns
            .Select((column, j) => x.Select(row => row.Values[j]).ToList())
            .Append([.. t])
            .Select(values => MeanAndStd(values, 1))
            .ToList();
        var kept = Enumerable.Range(0, x.Count)
            .Where(i => !IsOutlier([.. x[i].Values, t[i]], columns, threshold))
            .ToList();
        return ([.. kept.Select(i => x[i])], [.. kept.Select(i => t[i])]);
    }

    /// <summary>
    /// 外れ値を除き、テストデータを分けてから、残りを訓練データと検証データに分ける。
    /// 標準化と 2 次の項は、訓練データの平均値と標準偏差で 3 つすべてに適用する。
    /// </summary>
    public static BostonDataset Prepare(string csvFile, double testSize, double validationSize, int seed)
    {
        var (allX, allT) = RemoveOutliers(Load(csvFile), OutlierThreshold);
        var outer = Preprocessing.SplitTrainTest(allX, allT, testSize, seed);
        var inner = Preprocessing.SplitTrainTest(outer.XTrain, outer.TTrain, validationSize, seed);
        var scaler = PolynomialScaler.Fit(FeatureNames, inner.XTrain);
        return new BostonDataset(
            scaler.Transform(inner.XTrain),
            inner.TTrain,
            scaler.Transform(inner.XTest),
            inner.TTest,
            scaler.Transform(outer.XTest),
            outer.TTest,
            scaler.FeatureNames);
    }

    /// <summary>平均値と標準偏差。標準偏差は偏差の二乗和を「件数 - ddof」で割って求める。</summary>
    public static (double Mean, double Std) MeanAndStd(IReadOnlyList<double> values, int ddof)
    {
        var mean = values.Average();
        var squares = values.Sum(value => (value - mean) * (value - mean));
        return (mean, Math.Sqrt(squares / (values.Count - ddof)));
    }

    private static (IReadOnlyList<Features> X, IReadOnlyList<double> T) RemoveOutliers(
        (IReadOnlyList<Features> X, IReadOnlyList<double> T) data, double threshold) =>
        RemoveOutliers(data.X, data.T, threshold);

    private static bool IsOutlier(
        IReadOnlyList<double> row, List<(double Mean, double Std)> stats, double threshold) =>
        row.Where((value, j) => Math.Abs((value - stats[j].Mean) / stats[j].Std) > threshold).Any();

    private static double Value(Row row, string column) =>
        row.Number(column) ?? throw new InvalidDataException($"{column} が空欄です");
}

/// <summary>訓練データから求めた標準化の平均値・標準偏差と、2 次の項を作る列の組。</summary>
public sealed class PolynomialScaler
{
    private readonly string[] columns;
    private readonly (double Mean, double Std)[] stats;
    private readonly (int Left, int Right)[] pairs;

    private PolynomialScaler(
        string[] columns, (double Mean, double Std)[] stats, (int Left, int Right)[] pairs, string[] featureNames)
    {
        this.columns = columns;
        this.stats = stats;
        this.pairs = pairs;
        this.FeatureNames = featureNames;
    }

    /// <summary>標準化した列と 2 次の項の名前。</summary>
    public IReadOnlyList<string> FeatureNames { get; }

    /// <summary>平均値と、件数で割る標準偏差（母標準偏差）を訓練データから求め、2 次の項の組を決める。</summary>
    public static PolynomialScaler Fit(IReadOnlyList<string> columns, IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(columns);
        ArgumentNullException.ThrowIfNull(x);
        var pairs = columns
            .SelectMany((_, i) => Enumerable.Range(i, columns.Count - i).Select(j => (Left: i, Right: j)))
            .ToArray();
        var stats = columns
            .Select(column => Boston.MeanAndStd([.. x.Select(row => row.Value(column))], 0))
            .ToArray();
        return new PolynomialScaler(
            [.. columns],
            stats,
            pairs,
            [.. columns, .. pairs.Select(pair => Name(columns, pair))]);
    }

    /// <summary>標準化した値と、その 2 次の項を並べた行列にする。</summary>
    public Matrix Transform(IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(x);
        return Matrix.FromRows([.. x.Select(row =>
        {
            var z = this.columns
                .Select((column, j) => (row.Value(column) - this.stats[j].Mean) / this.stats[j].Std)
                .ToArray();
            return z.Concat(this.pairs.Select(pair => z[pair.Left] * z[pair.Right])).ToArray();
        })]);
    }

    /// <summary>2 次の項の名前。scikit-learn と同じ形（"RM^2"・"RM LSTAT"）にする。</summary>
    private static string Name(IReadOnlyList<string> columns, (int Left, int Right) pair) =>
        pair.Left == pair.Right ? $"{columns[pair.Left]}^2" : $"{columns[pair.Left]} {columns[pair.Right]}";
}
