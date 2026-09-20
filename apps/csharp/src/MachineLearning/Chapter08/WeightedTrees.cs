namespace MachineLearning.Chapter08;

using MachineLearning.Chapter02;
using MachineLearning.Chapter03;

/// <summary>1 件ごとの重みを使って決定木を作り、予測する関数。</summary>
public static class WeightedTrees
{
    /// <summary>重み付きのジニ不純度。ラベルの件数の代わりに重みの合計で割合を求める。</summary>
    public static double WeightedGini(IReadOnlyList<int> labels, IReadOnlyList<double> weights)
    {
        ArgumentNullException.ThrowIfNull(weights);
        var total = weights.Sum();
        return 1.0 - WeightSums(labels, weights).Values.Sum(weight => Math.Pow(weight / total, 2));
    }

    /// <summary>クラスの件数に反比例する重み（件数 / (クラスの数 × そのクラスの件数)）を 1 件ごとに求める。</summary>
    public static IReadOnlyList<double> BalancedWeights(IReadOnlyList<int> t)
    {
        ArgumentNullException.ThrowIfNull(t);
        var counts = new Dictionary<int, int>();
        foreach (var label in t)
        {
            counts[label] = counts.GetValueOrDefault(label) + 1;
        }

        return [.. t.Select(label => (double)t.Count / (counts.Count * counts[label]))];
    }

    /// <summary>重みの合計が最も大きいラベル。同じなら先に現れたラベルを選ぶ。</summary>
    public static int WeightedMajority(IReadOnlyList<int> labels, IReadOnlyList<double> weights) =>
        WeightSums(labels, weights).Aggregate((best, next) => next.Value > best.Value ? next : best).Key;

    /// <summary>左右の重み付き不純度の、重みによる平均が最も小さくなる分割。同じ不純度なら先に見つけた分割を選ぶ。</summary>
    public static Split? BestSplit(IReadOnlyList<Features> x, IReadOnlyList<int> t, IReadOnlyList<double> w)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        ArgumentNullException.ThrowIfNull(w);
        if (x.Count == 0 || WeightedGini(t, w) == 0.0)
        {
            return null;
        }

        Split? best = null;
        foreach (var feature in x[0].Columns)
        {
            var sorted = x
                .Select((features, i) => (Value: features.Value(feature), Label: t[i], Weight: w[i]))
                .OrderBy(row => row.Value)
                .ToList();
            var total = sorted.Sum(row => row.Weight);
            for (var i = 1; i < sorted.Count; i++)
            {
                if (sorted[i].Value == sorted[i - 1].Value)
                {
                    continue;
                }

                var impurity = (Impurity(sorted.Take(i)) + Impurity(sorted.Skip(i))) / total;
                if (best is null || impurity < best.Impurity)
                {
                    best = new Split(feature, (sorted[i - 1].Value + sorted[i].Value) / 2, impurity);
                }
            }
        }

        return best;
    }

    /// <summary>深さの上限まで分割を繰り返して木を作る。maxDepth が負なら上限なし。</summary>
    public static TreeNode Build(
        IReadOnlyList<Features> x, IReadOnlyList<int> t, IReadOnlyList<double> w, int maxDepth)
    {
        ArgumentNullException.ThrowIfNull(x);
        var split = maxDepth == 0 ? null : BestSplit(x, t, w);
        if (split is null)
        {
            return new LeafNode(WeightedMajority(t, w));
        }

        var left = Enumerable.Range(0, x.Count).Where(i => GoesLeft(split, x[i])).ToList();
        var right = Enumerable.Range(0, x.Count).Where(i => !GoesLeft(split, x[i])).ToList();
        var childDepth = maxDepth < 0 ? maxDepth : maxDepth - 1;
        return new SplitNode(
            split.Feature,
            split.Threshold,
            Build(Pick(x, left), Pick(t, left), Pick(w, left), childDepth),
            Build(Pick(x, right), Pick(t, right), Pick(w, right), childDepth));
    }

    /// <summary>1 件の特徴量のラベルを予測する。</summary>
    public static int PredictOne(TreeNode node, Features features) =>
        node switch
        {
            LeafNode leaf => leaf.Label,
            SplitNode split => PredictOne(
                features.Value(split.Feature) <= split.Threshold ? split.Left : split.Right, features),
            _ => throw new ArgumentException($"知らない木です: {node}", nameof(node)),
        };

    /// <summary>ラベルごとの重みの合計を、ラベルが先に現れた順に並べて返す。</summary>
    private static Dictionary<int, double> WeightSums(IReadOnlyList<int> labels, IReadOnlyList<double> weights)
    {
        ArgumentNullException.ThrowIfNull(labels);
        ArgumentNullException.ThrowIfNull(weights);
        var sums = new Dictionary<int, double>();
        for (var i = 0; i < labels.Count; i++)
        {
            sums[labels[i]] = sums.GetValueOrDefault(labels[i]) + weights[i];
        }

        return sums;
    }

    /// <summary>分割の片側の、重みの合計で重み付けしたジニ不純度。</summary>
    private static double Impurity(IEnumerable<(double Value, int Label, double Weight)> part)
    {
        var rows = part.ToList();
        var weights = rows.Select(row => row.Weight).ToList();
        return weights.Sum() * WeightedGini([.. rows.Select(row => row.Label)], weights);
    }

    private static bool GoesLeft(Split split, Features features) =>
        features.Value(split.Feature) <= split.Threshold;

    private static IReadOnlyList<T> Pick<T>(IReadOnlyList<T> values, IReadOnlyList<int> positions) =>
        [.. positions.Select(i => values[i])];
}
