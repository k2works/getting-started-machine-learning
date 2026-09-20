namespace MachineLearning.Chapter14;

using Microsoft.ML;
using Microsoft.ML.Data;
using Microsoft.ML.Trainers;
using Point = System.Collections.Generic.IReadOnlyList<double>;

/// <summary>ML.NET に渡す 1 行。列の長さは実行時に決まるので、SchemaDefinition で型を指定する。</summary>
public sealed class PointRow
{
    public float[] Features { get; set; } = [];
}

/// <summary>ML.NET の予測。クラスタ番号は 1 から始まる。</summary>
public sealed class ClusterPrediction
{
    public uint PredictedLabel { get; set; }
}

/// <summary>
/// ML.NET の K-means でクラスタリングする。初期中心は渡せないので（<see cref="KMeansTrainer.Options"/> には
/// 初期化のアルゴリズムを選ぶ設定しか無い）、自作と同じ初期中心での突き合わせはできない。SSE だけを比べる。
/// </summary>
public static class MlNetKMeans
{
    /// <summary>クラスタリングして、0 から始まるクラスタ番号を返す。</summary>
    public static IReadOnlyList<int> Cluster(int nClusters, int seed, IReadOnlyList<Point> points)
    {
        ArgumentNullException.ThrowIfNull(points);
        var context = new MLContext(seed: seed);
        var schema = SchemaDefinition.Create(typeof(PointRow));
        schema["Features"].ColumnType = new VectorDataViewType(NumberDataViewType.Single, points[0].Count);
        var data = context.Data.LoadFromEnumerable(
            points.Select(point => new PointRow { Features = [.. point.Select(value => (float)value)] }).ToList(),
            schema);
        var options = new KMeansTrainer.Options
        {
            NumberOfClusters = nClusters,
            InitializationAlgorithm = KMeansTrainer.InitializationAlgorithm.KMeansPlusPlus,
            NumberOfThreads = 1,
        };
        var model = context.Clustering.Trainers.KMeans(options).Fit(data);
        return [.. context.Data
            .CreateEnumerable<ClusterPrediction>(model.Transform(data), reuseRowObject: false)
            .Select(prediction => (int)prediction.PredictedLabel - 1)];
    }

    /// <summary>
    /// シードを 1 ずつずらして nInit 回クラスタリングし、自作と同じ式で求めた SSE の最小値を返す。
    /// 中心は、ML.NET が割り当てたクラスタごとの点の平均とする。
    /// </summary>
    public static double BestSse(int nClusters, int seed, int nInit, IReadOnlyList<Point> points)
    {
        ArgumentNullException.ThrowIfNull(points);
        return Enumerable.Range(seed, nInit).Min(s =>
        {
            var labels = Cluster(nClusters, s, points);
            var centers = KMeans.UpdateCenters(points, labels, [.. Enumerable.Repeat(points[0], nClusters)]);
            return KMeans.SumOfSquaredErrors(points, labels, centers);
        });
    }
}
