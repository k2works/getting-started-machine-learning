namespace MachineLearning.Chapter07;

using MachineLearning.Chapter02;
using Microsoft.ML;
using Microsoft.ML.Data;
using Microsoft.ML.Trainers;

/// <summary>ML.NET の回帰に渡す 1 行。ML.NET は書き換えられるプロパティを持つクラスを求める。</summary>
public sealed class MlRegressionRow
{
    public float[] Features { get; set; } = [];

    public float Label { get; set; }
}

/// <summary>ML.NET が返す予測。列の名前（Score）でプロパティに対応づけられる。</summary>
public sealed class MlRegressionPrediction
{
    public float Score { get; set; }
}

/// <summary>
/// ML.NET の線形回帰（SDCA）と突き合わせる。
/// ML.NET の最小二乗法（Ols）は Microsoft.ML.Mkl.Components が必要で、その Intel MKL のネイティブライブラリは
/// x64 専用なので arm64 では動かない（ADR 004）。そこで反復で解く SDCA を使う。
/// </summary>
public static class MlNetRegression
{
    /// <summary>
    /// SDCA で学習し、予測する関数を返す。SDCA は特徴量の大きさに敏感なので、
    /// 平均 0・分散 1 に正規化する変換をパイプラインの前に置く。
    /// </summary>
    public static Func<IReadOnlyList<Features>, IReadOnlyList<double>> TrainSdca(
        IReadOnlyList<Features> x, IReadOnlyList<double> t, int iterations = 100)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        var context = new MLContext(seed: 0);
        var schema = SchemaFor(x[0].Columns.Count);
        var data = context.Data.LoadFromEnumerable(ToRows(x, i => (float)t[i]), schema);
        // SDCA は既定では複数のスレッドで進めるので、実行するたびに結果が変わる。
        // 記事とテストで同じ値を再現できるように、スレッドを 1 本に絞る
        var options = new SdcaRegressionTrainer.Options
        {
            L2Regularization = 1e-7f,
            L1Regularization = null,
            MaximumNumberOfIterations = iterations,
            NumberOfThreads = 1,
        };
        var pipeline = context.Transforms.NormalizeMeanVariance("Features")
            .Append(context.Regression.Trainers.Sdca(options));
        var model = pipeline.Fit(data);

        return newX =>
        {
            var newData = context.Data.LoadFromEnumerable(ToRows(newX, _ => 0f), schema);
            return [.. context.Data
                .CreateEnumerable<MlRegressionPrediction>(model.Transform(newData), reuseRowObject: false)
                .Select(prediction => (double)prediction.Score)];
        };
    }

    private static List<MlRegressionRow> ToRows(IReadOnlyList<Features> x, Func<int, float> label) =>
        [.. x.Select((features, i) => new MlRegressionRow
        {
            Features = [.. features.Values.Select(value => (float)value)],
            Label = label(i),
        })];

    /// <summary>特徴量の数は実行時に決まるので、Features 列のベクトルの長さをスキーマで指定する（第 3 章と同じ）。</summary>
    private static SchemaDefinition SchemaFor(int featureCount)
    {
        var schema = SchemaDefinition.Create(typeof(MlRegressionRow));
        schema["Features"].ColumnType = new VectorDataViewType(NumberDataViewType.Single, featureCount);
        return schema;
    }
}
