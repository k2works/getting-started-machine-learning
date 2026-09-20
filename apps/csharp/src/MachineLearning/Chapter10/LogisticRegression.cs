namespace MachineLearning.Chapter10;

using MachineLearning.Chapter02;

/// <summary>ロジスティック回帰の学習の設定。</summary>
public sealed record LogisticSettings(double LearningRate, int Epochs)
{
    /// <summary>既定の設定（学習率 1.0、繰り返し 5000 回）。</summary>
    public static LogisticSettings Default { get; } = new(1.0, 5000);
}

/// <summary>学習したロジスティック回帰のモデル。</summary>
public sealed class LogisticModel
{
    private readonly double[][] weights;
    private readonly double[] bias;

    private LogisticModel(
        IReadOnlyList<string> features,
        IReadOnlyList<string> classes,
        double[][] weights,
        double[] bias,
        IReadOnlyList<double> losses)
    {
        this.Features = features;
        this.Classes = classes;
        this.weights = weights;
        this.bias = bias;
        this.Losses = losses;
    }

    /// <summary>特徴量の列名（重みの行の順）。</summary>
    public IReadOnlyList<string> Features { get; }

    /// <summary>品種（重みの列の順）。</summary>
    public IReadOnlyList<string> Classes { get; }

    /// <summary>繰り返しごとの損失（交差エントロピー）。</summary>
    public IReadOnlyList<double> Losses { get; }

    /// <summary>特徴量ごとの重み（Weights[特徴量][品種]）。</summary>
    public IReadOnlyList<IReadOnlyList<double>> Weights => this.weights;

    /// <summary>品種ごとの切片。</summary>
    public IReadOnlyList<double> Bias => this.bias;

    /// <summary>バッチ勾配降下法で重みと切片を学習する。</summary>
    public static LogisticModel Learn(
        LogisticSettings settings, IReadOnlyList<Features> x, IReadOnlyList<string> t)
    {
        ArgumentNullException.ThrowIfNull(settings);
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        var features = x[0].Columns;
        var rows = x.Select(row => row.Values.ToArray()).ToArray();
        var classes = t.Distinct(StringComparer.Ordinal).Order(StringComparer.Ordinal).ToArray();
        var targets = t.Select(label => Array.IndexOf(classes, label)).ToArray();
        double n = rows.Length;

        var weights = Enumerable.Range(0, features.Count).Select(_ => new double[classes.Length]).ToArray();
        var bias = new double[classes.Length];
        var losses = new List<double>(settings.Epochs);

        for (var epoch = 0; epoch < settings.Epochs; epoch++)
        {
            var probabilities = rows.Select(row => LogisticRegression.Softmax(Scores(weights, bias, row))).ToArray();

            // 確率 − 正解（正解の品種だけ 1 を引く）
            var errors = probabilities
                .Select((p, i) => p.Select((value, k) => k == targets[i] ? value - 1.0 : value).ToArray())
                .ToArray();

            for (var f = 0; f < features.Count; f++)
            {
                for (var k = 0; k < classes.Length; k++)
                {
                    var gradient = rows.Select((row, i) => row[f] * errors[i][k]).Sum();
                    weights[f][k] -= settings.LearningRate * gradient / n;
                }
            }

            for (var k = 0; k < classes.Length; k++)
            {
                bias[k] -= settings.LearningRate * errors.Sum(error => error[k]) / n;
            }

            losses.Add(LogisticRegression.CrossEntropy(probabilities, targets));
        }

        return new LogisticModel(features, classes, weights, bias, losses);
    }

    /// <summary>スコアが最大の品種を予測する（ソフトマックスは大小関係を変えないので確率は計算しない）。</summary>
    public IReadOnlyList<string> Predict(IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(x);
        return [.. x.Select(row =>
        {
            var scores = Scores(this.weights, this.bias, [.. this.Features.Select(row.Value)]);
            return this.Classes[Array.IndexOf(scores, scores.Max())];
        })];
    }

    /// <summary>品種ごとの「特徴量の重み付きの和 + 切片」。</summary>
    private static double[] Scores(double[][] weights, double[] bias, double[] row) =>
        [.. bias.Select((b, k) => b + row.Select((value, f) => value * weights[f][k]).Sum())];
}

/// <summary>ソフトマックスのロジスティック回帰。学習したモデルで予測する分類器。</summary>
public sealed class LogisticRegression(LogisticSettings settings) : IClassifier
{
    /// <summary>確率が 0 のときに log 0 が負の無限大にならないように足す小さな値。</summary>
    private const double Epsilon = 1e-12;

    /// <summary>スコアを確率に変換する。最大値を引いてから exp を計算して、大きな値でもあふれないようにする。</summary>
    public static double[] Softmax(IReadOnlyList<double> z)
    {
        ArgumentNullException.ThrowIfNull(z);
        var max = z.Max();
        var exps = z.Select(value => Math.Exp(value - max)).ToArray();
        var total = exps.Sum();
        return [.. exps.Select(value => value / total)];
    }

    /// <summary>交差エントロピー。正解の品種の確率の対数の平均にマイナスを付けたもの。</summary>
    public static double CrossEntropy(IReadOnlyList<double[]> probabilities, IReadOnlyList<int> targets)
    {
        ArgumentNullException.ThrowIfNull(probabilities);
        ArgumentNullException.ThrowIfNull(targets);
        return -probabilities.Select((p, i) => Math.Log(p[targets[i]] + Epsilon)).Average();
    }

    public Func<IReadOnlyList<Features>, IReadOnlyList<string>> Fit(
        IReadOnlyList<Features> x, IReadOnlyList<string> t) =>
        LogisticModel.Learn(settings, x, t).Predict;
}
