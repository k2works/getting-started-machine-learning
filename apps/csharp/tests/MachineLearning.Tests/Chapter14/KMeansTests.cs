namespace MachineLearning.Tests.Chapter14;

using MachineLearning.Chapter14;
using Point = System.Collections.Generic.IReadOnlyList<double>;

public class KMeansTests
{
    /// <summary>左下と右上に 2 つずつ、はっきり離れた架空の点。</summary>
    private static readonly IReadOnlyList<Point> TwoGroups =
        [[0.0, 0.0], [0.0, 1.0], [10.0, 0.0], [10.0, 1.0]];

    [Fact(DisplayName = "2 点間のユークリッド距離の 2 乗を求める")]
    public void ComputesSquaredDistance() =>
        Assert.Equal(25.0, KMeans.SquaredDistance([0.0, 0.0], [3.0, 4.0]));

    [Fact(DisplayName = "各点を最も近い中心に割り当てる")]
    public void AssignsToNearestCenter() =>
        Assert.Equal([0, 0, 1, 1], KMeans.AssignClusters([[0.0, 0.0], [10.0, 0.0]], TwoGroups));

    [Fact(DisplayName = "距離が同じなら、番号の小さい中心に割り当てる")]
    public void PrefersSmallerIndexOnTie() =>
        Assert.Equal([0], KMeans.AssignClusters([[0.0], [2.0]], [[1.0]]));

    [Fact(DisplayName = "新しい中心は、割り当てられた点の平均になる")]
    public void UpdatesCentersToMean()
    {
        var centers = KMeans.UpdateCenters(TwoGroups, [0, 0, 1, 1], [[0.0, 0.0], [10.0, 0.0]]);

        Assert.Equal([0.0, 0.5], centers[0]);
        Assert.Equal([10.0, 0.5], centers[1]);
    }

    [Fact(DisplayName = "点が 1 つも割り当てられなかったクラスタは、前の中心を残す")]
    public void KeepsCenterOfEmptyCluster()
    {
        var centers = KMeans.UpdateCenters(TwoGroups, [0, 0, 0, 0], [[0.0, 0.0], [99.0, 99.0]]);

        Assert.Equal([99.0, 99.0], centers[1]);
    }

    [Fact(DisplayName = "SSE は、各点と属するクラスタの中心との距離の 2 乗の合計になる")]
    public void ComputesSumOfSquaredErrors() =>
        Assert.Equal(1.0, KMeans.SumOfSquaredErrors(TwoGroups, [0, 0, 1, 1], [[0.0, 0.5], [10.0, 0.5]]));

    [Fact(DisplayName = "離れた 2 つの集まりを、中心が動かなくなるまで繰り返して分ける")]
    public void SeparatesTwoGroups()
    {
        var result = KMeans.Fit([[0.0, 0.0], [9.0, 9.0]], TwoGroups);

        Assert.Equal([0, 0, 1, 1], result.Labels);
        Assert.Equal([0.0, 0.5], result.Centers[0]);
        Assert.Equal([10.0, 0.5], result.Centers[1]);
        Assert.Equal(1.0, result.Sse);
    }

    [Fact(DisplayName = "同じ初期中心なら、何度実行しても同じ結果になる")]
    public void IsDeterministic()
    {
        var first = KMeans.Fit([[0.0, 0.0], [9.0, 9.0]], TwoGroups);
        var second = KMeans.Fit([[0.0, 0.0], [9.0, 9.0]], TwoGroups);

        Assert.Equal(first.Labels, second.Labels);
        Assert.Equal(first.Sse, second.Sse);
    }

    [Fact(DisplayName = "反復回数の上限で打ち切ると、収束する前の中心になる")]
    public void StopsAtMaxIterations()
    {
        // 1 回目の更新では、右側の 2 点しか中心 (9, 9) に割り当てられない
        var result = KMeans.Fit(1, [[0.0, 0.0], [9.0, 9.0]], TwoGroups);

        Assert.Equal([0.0, 0.5], result.Centers[0]);
        Assert.Equal([10.0, 0.5], result.Centers[1]);
    }

    [Fact(DisplayName = "初期中心は、シードで並べ替えた先頭の点になる")]
    public void ChoosesInitialCentersBySeed()
    {
        var centers = KMeans.ChooseInitialCenters(2, 0, TwoGroups);

        Assert.Equal(2, centers.Count);
        Assert.All(centers, center => Assert.Contains(center, TwoGroups));
        Assert.NotEqual(centers[0], centers[1]);
        Assert.Equal(centers, KMeans.ChooseInitialCenters(2, 0, TwoGroups));
    }

    [Fact(DisplayName = "初期中心の候補のうち、SSE が最小の結果を選ぶ")]
    public void ChoosesBestCandidate()
    {
        // 同じ側の 2 点を初期中心にすると、右の集まりが 1 つのクラスタにまとまらない
        var bad = KMeans.Fit([[0.0, 0.0], [0.0, 1.0]], TwoGroups);

        var best = KMeans.Best([[[0.0, 0.0], [0.0, 1.0]], [[0.0, 0.0], [10.0, 0.0]]], TwoGroups);

        Assert.True(bad.Sse > best.Sse);
        Assert.Equal(1.0, best.Sse);
    }

    [Fact(DisplayName = "クラスタ数を増やすと SSE は下がる")]
    public void SseDecreasesWithMoreClusters()
    {
        var sse = KMeans.SseByClusterCount(5, 0, [1, 2, 3], TwoGroups);

        Assert.Equal([1, 2, 3], sse.Select(pair => pair.Key));
        Assert.True(sse[0].Value > sse[1].Value);
        Assert.True(sse[1].Value >= sse[2].Value);
    }

    [Fact(DisplayName = "クラスタ数 1 の SSE は、全体の平均からの距離の 2 乗の合計になる")]
    public void SseOfSingleCluster() =>
        Assert.Equal(
            KMeans.SumOfSquaredErrors(TwoGroups, [0, 0, 0, 0], [[5.0, 0.5]]),
            KMeans.FitWithRestarts(3, 1, 0, TwoGroups).Sse,
            10);
}
