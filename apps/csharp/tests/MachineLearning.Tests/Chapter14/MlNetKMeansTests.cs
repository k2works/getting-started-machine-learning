namespace MachineLearning.Tests.Chapter14;

using MachineLearning.Chapter14;
using Microsoft.ML.Trainers;
using Point = System.Collections.Generic.IReadOnlyList<double>;

/// <summary>ML.NET の K-means の振る舞いを確かめる学習用テスト。</summary>
public class MlNetKMeansTests
{
    private static readonly IReadOnlyList<Point> TwoGroups =
        [[0.0, 0.0], [0.0, 1.0], [10.0, 0.0], [10.0, 1.0]];

    [Fact(DisplayName = "KMeansTrainer.Options には初期中心を渡す設定が無い")]
    public void HasNoInitialCentroidsOption()
    {
        var names = typeof(KMeansTrainer.Options).GetFields().Select(field => field.Name).ToList();

        Assert.Contains("InitializationAlgorithm", names);
        Assert.DoesNotContain(names, name => name.Contains("Centroid", StringComparison.Ordinal));
    }

    [Fact(DisplayName = "離れた 2 つの集まりを、自作と同じ分け方でクラスタリングする")]
    public void SeparatesTwoGroups()
    {
        var labels = MlNetKMeans.Cluster(2, 0, TwoGroups);

        // クラスタ番号の付き方は自作と違いうるので、同じ集まりが同じ番号になることだけを確かめる
        Assert.Equal(labels[0], labels[1]);
        Assert.Equal(labels[2], labels[3]);
        Assert.NotEqual(labels[0], labels[2]);
    }

    [Fact(DisplayName = "クラスタ番号は 0 から始まる番号に直してある")]
    public void ShiftsClusterNumbersToZeroBased() =>
        Assert.Equal([0, 1], MlNetKMeans.Cluster(2, 0, TwoGroups).Distinct().Order());

    [Fact(DisplayName = "SSE は自作と同じ式で求めるので、同じ分け方なら同じ値になる")]
    public void ComputesSameSseAsMine() =>
        Assert.Equal(KMeans.FitWithRestarts(5, 2, 0, TwoGroups).Sse, MlNetKMeans.BestSse(2, 0, 5, TwoGroups), 10);
}
