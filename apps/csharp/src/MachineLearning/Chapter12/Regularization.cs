namespace MachineLearning.Chapter12;

using MachineLearning.Chapter07;

/// <summary>正則化した線形回帰の学習結果。係数は特徴量の列の順に並ぶ。</summary>
/// <param name="Coefficients">特徴量ごとの係数</param>
/// <param name="Intercept">切片</param>
public sealed record RegularizedModel(IReadOnlyList<double> Coefficients, double Intercept);

/// <summary>正則化の強さ 1 つ分の実験結果。</summary>
/// <param name="Alpha">正則化の強さ</param>
/// <param name="TrainScore">訓練データの決定係数</param>
/// <param name="ValidationScore">検証データの決定係数</param>
/// <param name="CoefficientAbsSum">係数の絶対値の合計</param>
public sealed record Experiment(double Alpha, double TrainScore, double ValidationScore, double CoefficientAbsSum);

/// <summary>リッジ回帰とラッソ回帰。</summary>
public static class Regularization
{
    /// <summary>座標降下法の繰り返しの回数（全係数を 1 回ずつ更新するのを 1 回と数える）。</summary>
    public const int LassoIterations = 1000;

    /// <summary>中心化した X と t で (Xᵀ X + alpha I) w = Xᵀ t を解く。切片には罰則をかけない。</summary>
    public static RegularizedModel FitRidge(double alpha, Matrix x, IReadOnlyList<double> t)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        var xMeans = x.ColumnMeans();
        var tMean = t.Average();
        var centered = x.Center(xMeans);
        var transposed = centered.Transpose();
        var coefficients = transposed
            .Multiply(centered)
            .Add(MatrixOperations.Identity(x.ColumnCount).Scale(alpha))
            .Solve(transposed.Multiply([.. t.Select(value => value - tMean)]));
        return new RegularizedModel(coefficients, tMean - Matrix.Dot(xMeans, coefficients));
    }

    /// <summary>(1/2n) × 誤差の二乗和 + alpha × 係数の絶対値の和 を、座標降下法で最小化する（ラッソ回帰）。</summary>
    public static RegularizedModel FitLasso(double alpha, Matrix x, IReadOnlyList<double> t)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        double n = x.RowCount;
        var xMeans = x.ColumnMeans();
        var tMean = t.Average();
        var centered = x.Center(xMeans).Transpose();
        var columns = Enumerable.Range(0, x.ColumnCount).Select(j => centered.Row(j).ToArray()).ToArray();
        var squaredNorms = columns.Select(column => Matrix.Dot(column, column) / n).ToArray();
        var weights = new double[columns.Length];

        // 残差（中心化した t - 現在の予測）。係数を 1 つ変えるたびに、その分だけ更新する
        var residual = t.Select(value => value - tMean).ToArray();
        for (var iteration = 0; iteration < LassoIterations; iteration++)
        {
            for (var j = 0; j < columns.Length; j++)
            {
                var column = columns[j];
                var rho = (Matrix.Dot(column, residual) / n) + (weights[j] * squaredNorms[j]);
                var updated = SoftThreshold(alpha, rho) / squaredNorms[j];
                for (var i = 0; i < residual.Length; i++)
                {
                    residual[i] += column[i] * (weights[j] - updated);
                }

                weights[j] = updated;
            }
        }

        return new RegularizedModel(weights, tMean - Matrix.Dot(xMeans, weights));
    }

    /// <summary>特徴量と係数の内積 + 切片を行ごとに求める。</summary>
    public static IReadOnlyList<double> Predict(RegularizedModel model, Matrix x)
    {
        ArgumentNullException.ThrowIfNull(model);
        ArgumentNullException.ThrowIfNull(x);
        return [.. Enumerable.Range(0, x.RowCount)
            .Select(i => model.Intercept + Matrix.Dot(x.Row(i), model.Coefficients))];
    }

    /// <summary>alpha ごとに訓練データで学習し、訓練データと検証データの決定係数、係数の絶対値の合計を記録する。</summary>
    public static IReadOnlyList<Experiment> RunRidgeExperiments(
        IReadOnlyList<double> alphas,
        Matrix xTrain,
        IReadOnlyList<double> tTrain,
        Matrix xValid,
        IReadOnlyList<double> tValid)
    {
        ArgumentNullException.ThrowIfNull(alphas);
        return [.. alphas.Select(alpha =>
        {
            var model = FitRidge(alpha, xTrain, tTrain);
            return new Experiment(
                alpha,
                RegressionMetrics.R2Score(tTrain, Predict(model, xTrain)),
                RegressionMetrics.R2Score(tValid, Predict(model, xValid)),
                model.Coefficients.Sum(Math.Abs));
        })];
    }

    /// <summary>検証データの決定係数が最も高い実験。</summary>
    public static Experiment BestExperiment(IReadOnlyList<Experiment> experiments)
    {
        ArgumentNullException.ThrowIfNull(experiments);
        return experiments.Count == 0
            ? throw new ArgumentException("実験が 1 つもありません", nameof(experiments))
            : experiments.MaxBy(experiment => experiment.ValidationScore)!;
    }

    /// <summary>係数がちょうど 0 の特徴量の名前。</summary>
    public static IReadOnlyList<string> ZeroCoefficientNames(
        IReadOnlyList<double> coefficients, IReadOnlyList<string> featureNames)
    {
        ArgumentNullException.ThrowIfNull(coefficients);
        ArgumentNullException.ThrowIfNull(featureNames);
        return [.. featureNames.Where((_, i) => coefficients[i] == 0.0)];
    }

    /// <summary>軟閾値関数。z の絶対値を gamma だけ 0 に近づけ、0 を越えるならちょうど 0 にする。</summary>
    private static double SoftThreshold(double gamma, double z) =>
        z > gamma ? z - gamma : z < -gamma ? z + gamma : 0.0;
}
