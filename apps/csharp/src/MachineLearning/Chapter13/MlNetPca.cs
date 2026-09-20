namespace MachineLearning.Chapter13;

using MachineLearning.Chapter07;
using Microsoft.ML;
using Microsoft.ML.Data;

/// <summary>ML.NET に渡す 1 行。列の長さは実行時に決まるので、SchemaDefinition で型を指定する。</summary>
public sealed class FeatureRow
{
    public float[] Features { get; set; } = [];
}

/// <summary>ML.NET の予測（主成分の座標）。</summary>
public sealed class ProjectedRow
{
    public float[] Projected { get; set; } = [];
}

/// <summary>
/// ML.NET の ProjectToPrincipalComponents で主成分の座標に変換する。
/// ML.NET の主成分分析はランダム化 PCA で、主成分や寄与率を取り出す API は無く、座標だけが得られる。
/// </summary>
public static class MlNetPca
{
    public static Matrix Project(int rank, bool ensureZeroMean, int seed, Matrix x)
    {
        ArgumentNullException.ThrowIfNull(x);
        var context = new MLContext(seed: seed);
        var schema = SchemaDefinition.Create(typeof(FeatureRow));
        schema["Features"].ColumnType = new VectorDataViewType(NumberDataViewType.Single, x.ColumnCount);
        var rows = Enumerable.Range(0, x.RowCount)
            .Select(row => new FeatureRow { Features = [.. x.Row(row).Select(value => (float)value)] })
            .ToList();
        var data = context.Data.LoadFromEnumerable(rows, schema);
        var transformed = context.Transforms
            .ProjectToPrincipalComponents("Projected", "Features", rank: rank, ensureZeroMean: ensureZeroMean, seed: seed)
            .Fit(data)
            .Transform(data);
        return Matrix.FromRows([.. context.Data
            .CreateEnumerable<ProjectedRow>(transformed, reuseRowObject: false)
            .Select(row => row.Projected.Select(value => (double)value).ToArray())]);
    }
}
