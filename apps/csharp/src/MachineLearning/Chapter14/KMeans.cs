namespace MachineLearning.Chapter14;

using MachineLearning.Chapter02;
using Point = System.Collections.Generic.IReadOnlyList<double>;

/// <summary>クラスタリングの結果。</summary>
/// <param name="Labels">点ごとのクラスタ番号</param>
/// <param name="Centers">クラスタごとの中心</param>
/// <param name="Sse">各点と、その点が属するクラスタの中心との距離の 2 乗の合計</param>
public sealed record KMeansResult(IReadOnlyList<int> Labels, IReadOnlyList<Point> Centers, double Sse);

/// <summary>
/// 点を、最も近い中心のクラスタに割り当てることを繰り返す K-means。
/// 初期中心は引数で受け取り、選び方（ランダム・やり直し）は外側で決める。
/// </summary>
public static class KMeans
{
    /// <summary>反復回数の上限の既定値。</summary>
    public const int MaxIterations = 300;

    /// <summary>2 点間のユークリッド距離の 2 乗。大小を比べるだけなので平方根は取らない。</summary>
    public static double SquaredDistance(Point a, Point b)
    {
        ArgumentNullException.ThrowIfNull(a);
        ArgumentNullException.ThrowIfNull(b);
        var sum = 0.0;
        for (var i = 0; i < a.Count; i++)
        {
            sum += (a[i] - b[i]) * (a[i] - b[i]);
        }

        return sum;
    }

    /// <summary>各点を、最も近い中心の番号（クラスタ番号）に割り当てる。</summary>
    public static IReadOnlyList<int> AssignClusters(IReadOnlyList<Point> centers, IReadOnlyList<Point> points)
    {
        ArgumentNullException.ThrowIfNull(centers);
        ArgumentNullException.ThrowIfNull(points);
        return [.. points.Select(point => Enumerable.Range(0, centers.Count)
            .MinBy(k => SquaredDistance(point, centers[k])))];
    }

    /// <summary>クラスタごとに、割り当てられた点の平均を新しい中心にする。点が無ければ前の中心を残す。</summary>
    public static IReadOnlyList<Point> UpdateCenters(
        IReadOnlyList<Point> points, IReadOnlyList<int> labels, IReadOnlyList<Point> previousCenters)
    {
        ArgumentNullException.ThrowIfNull(points);
        ArgumentNullException.ThrowIfNull(labels);
        ArgumentNullException.ThrowIfNull(previousCenters);
        return [.. previousCenters.Select((previous, k) =>
        {
            var members = points.Where((_, i) => labels[i] == k).ToList();
            if (members.Count == 0)
            {
                return previous;
            }

            // コレクション式の前には型変換を書けないので、型を書いた変数に受けてから返す
            Point center = [.. Enumerable.Range(0, previous.Count).Select(j => members.Average(point => point[j]))];
            return center;
        })];
    }

    /// <summary>SSE。各点と、その点が属するクラスタの中心との距離の 2 乗の合計。</summary>
    public static double SumOfSquaredErrors(
        IReadOnlyList<Point> points, IReadOnlyList<int> labels, IReadOnlyList<Point> centers)
    {
        ArgumentNullException.ThrowIfNull(points);
        ArgumentNullException.ThrowIfNull(labels);
        ArgumentNullException.ThrowIfNull(centers);
        return points.Select((point, i) => SquaredDistance(point, centers[labels[i]])).Sum();
    }

    /// <summary>反復回数の上限を既定値にして、初期中心から K-means でクラスタリングする。</summary>
    public static KMeansResult Fit(IReadOnlyList<Point> initialCenters, IReadOnlyList<Point> points) =>
        Fit(MaxIterations, initialCenters, points);

    /// <summary>
    /// 中心が変わらなくなるか、反復回数が上限に達するまで、割り当てと中心の更新を繰り返す。
    /// 初期中心を引数で受け取るので、同じ初期中心を渡せば何度でも同じ結果になる。
    /// </summary>
    public static KMeansResult Fit(int maxIterations, IReadOnlyList<Point> initialCenters, IReadOnlyList<Point> points)
    {
        ArgumentNullException.ThrowIfNull(initialCenters);
        ArgumentNullException.ThrowIfNull(points);
        var centers = initialCenters;
        for (var i = 0; i < maxIterations; i++)
        {
            var next = UpdateCenters(points, AssignClusters(centers, points), centers);
            var settled = SameCenters(centers, next);
            centers = next;
            if (settled)
            {
                break;
            }
        }

        var labels = AssignClusters(centers, points);
        return new KMeansResult(labels, centers, SumOfSquaredErrors(points, labels, centers));
    }

    /// <summary>点の番号をシード付きで並べ替え、先頭の nClusters 個の点を初期中心にする。</summary>
    public static IReadOnlyList<Point> ChooseInitialCenters(int nClusters, int seed, IReadOnlyList<Point> points)
    {
        ArgumentNullException.ThrowIfNull(points);
        return [.. Preprocessing.Shuffle([.. Enumerable.Range(0, points.Count)], seed)
            .Take(nClusters)
            .Select(i => points[i])];
    }

    /// <summary>初期中心の候補ごとにクラスタリングし、SSE が最小の結果を返す。</summary>
    public static KMeansResult Best(IReadOnlyList<IReadOnlyList<Point>> candidates, IReadOnlyList<Point> points)
    {
        ArgumentNullException.ThrowIfNull(candidates);
        return candidates.Select(initialCenters => Fit(initialCenters, points)).MinBy(result => result.Sse)!;
    }

    /// <summary>シードを 1 ずつずらして初期中心を nInit 通り選び、SSE が最小の結果を返す。</summary>
    public static KMeansResult FitWithRestarts(int nInit, int nClusters, int seed, IReadOnlyList<Point> points) =>
        Best(
            [.. Enumerable.Range(0, nInit).Select(i => ChooseInitialCenters(nClusters, seed + i, points))],
            points);

    /// <summary>クラスタ数ごとに、初期中心を nInit 通り試した最小の SSE を求める（エルボー法）。</summary>
    public static IReadOnlyList<KeyValuePair<int, double>> SseByClusterCount(
        int nInit, int seed, IReadOnlyList<int> clusterCounts, IReadOnlyList<Point> points)
    {
        ArgumentNullException.ThrowIfNull(clusterCounts);
        return [.. clusterCounts.Select(n =>
            KeyValuePair.Create(n, FitWithRestarts(nInit, n, seed, points).Sse))];
    }

    private static bool SameCenters(IReadOnlyList<Point> a, IReadOnlyList<Point> b) =>
        a.Zip(b).All(pair => pair.First.SequenceEqual(pair.Second));
}
