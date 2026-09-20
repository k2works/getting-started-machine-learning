namespace MachineLearning.Chapter12;

using MachineLearning.Chapter07;
using Microsoft.ML;
using Microsoft.ML.Data;
using Microsoft.ML.Trainers;

/// <summary>ML.NET に渡す 1 行。特徴量も正解も float で渡す。</summary>
public sealed class RegressionRow
{
    public float[] Features { get; set; } = [];

    public float Label { get; set; }
}

/// <summary>ML.NET の SDCA に L2 の罰則をかけ、自作のリッジ回帰と突き合わせる。</summary>
public static class MlNetRegularization
{
    /// <summary>自作と突き合わせられるように、収束の判定を厳しくする。</summary>
    public const float ConvergenceTolerance = 1e-7f;

    /// <summary>繰り返しの上限。</summary>
    public const int MaximumNumberOfIterations = 10000;

    /// <summary>
    /// ML.NET の SDCA で、(1/n) × 誤差の二乗和 + (l2 / 2) × 係数の二乗和 を最小化する。L1 の罰則は 0 にする。
    /// 第 7 章と同じく、順番を固定して 1 スレッドで学習し、実行のたびに結果が変わらないようにする。
    /// </summary>
    public static RegularizedModel FitSdca(double l2, Matrix x, IReadOnlyList<double> t)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        var context = new MLContext(seed: 0);
        var schema = SchemaDefinition.Create(typeof(RegressionRow));
        schema["Features"].ColumnType = new VectorDataViewType(NumberDataViewType.Single, x.ColumnCount);
        var rows = Enumerable.Range(0, x.RowCount)
            .Select(i => new RegressionRow
            {
                Features = [.. x.Row(i).Select(value => (float)value)],
                Label = (float)t[i],
            })
            .ToList();
        var options = new SdcaRegressionTrainer.Options
        {
            L2Regularization = (float)l2,
            L1Regularization = 0f,
            ConvergenceTolerance = ConvergenceTolerance,
            MaximumNumberOfIterations = MaximumNumberOfIterations,
            Shuffle = false,
            NumberOfThreads = 1,
        };
        var trained = context.Regression.Trainers.Sdca(options)
            .Fit(context.Data.LoadFromEnumerable(rows, schema));
        return new RegularizedModel(
            [.. trained.Model.Weights.Select(weight => (double)weight)], trained.Model.Bias);
    }

    /// <summary>自作の FitRidge と同じ alpha でリッジ回帰を学習する。ML.NET の l2 は alpha × 2 / 件数 に当たる。</summary>
    public static RegularizedModel FitRidgeWithMlNet(double alpha, Matrix x, IReadOnlyList<double> t)
    {
        ArgumentNullException.ThrowIfNull(t);
        return FitSdca(2.0 * alpha / t.Count, x, t);
    }
}
