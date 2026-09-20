namespace MachineLearning.Chapter03;

using System.Globalization;
using MachineLearning.Chapter02;

/// <summary>決定木を作り、予測し、表示する関数。</summary>
public static class DecisionTrees
{
    /// <summary>ジニ不純度。ラベルが 1 種類なら 0 で、種類が多く均等なほど大きい。</summary>
    public static double Gini(IReadOnlyList<string> labels)
    {
        ArgumentNullException.ThrowIfNull(labels);
        double total = labels.Count;
        return 1.0 - Counts(labels).Values.Sum(count => Math.Pow(count / total, 2));
    }

    /// <summary>左右の不純度の重み付き平均が最も小さくなる分割。同じ不純度なら先に見つけた（列の順で前の）分割を選ぶ。</summary>
    public static Split? BestSplit(IReadOnlyList<Features> x, IReadOnlyList<string> t)
    {
        ArgumentNullException.ThrowIfNull(x);
        ArgumentNullException.ThrowIfNull(t);
        if (Gini(t) == 0.0)
        {
            return null;
        }

        Split? best = null;
        foreach (var feature in x[0].Columns)
        {
            var sorted = x.Select((features, i) => (Value: features.Value(feature), Label: t[i]))
                .OrderBy(pair => pair.Value)
                .ToList();
            for (var i = 1; i < sorted.Count; i++)
            {
                if (sorted[i].Value == sorted[i - 1].Value)
                {
                    continue;
                }

                var left = sorted.Take(i).Select(pair => pair.Label).ToList();
                var right = sorted.Skip(i).Select(pair => pair.Label).ToList();
                var impurity = ((left.Count * Gini(left)) + (right.Count * Gini(right))) / sorted.Count;
                if (best is null || impurity < best.Impurity)
                {
                    best = new Split(feature, (sorted[i - 1].Value + sorted[i].Value) / 2, impurity);
                }
            }
        }

        return best;
    }

    /// <summary>1 件の特徴量のラベルを予測する。</summary>
    public static string PredictOne(Tree tree, Features features) =>
        tree switch
        {
            Leaf leaf => leaf.Label,
            Node node => PredictOne(GoesLeft(node.Split, features) ? node.Left : node.Right, features),
            _ => throw new ArgumentException($"知らない木です: {tree}", nameof(tree)),
        };

    /// <summary>木を、条件ごとに字下げした文字列にする。</summary>
    public static string Format(Tree tree) => Format(tree, string.Empty);

    /// <summary>深さの上限まで分割を繰り返して木を作る。maxDepth が負なら上限なし。</summary>
    internal static Tree Build(IReadOnlyList<Features> x, IReadOnlyList<string> t, int maxDepth)
    {
        var split = maxDepth == 0 ? null : BestSplit(x, t);
        if (split is null)
        {
            return new Leaf(Majority(t));
        }

        var left = Enumerable.Range(0, x.Count).Where(i => GoesLeft(split, x[i])).ToList();
        var right = Enumerable.Range(0, x.Count).Where(i => !GoesLeft(split, x[i])).ToList();
        var childDepth = maxDepth < 0 ? maxDepth : maxDepth - 1;
        return new Node(
            split,
            Build(Pick(x, left), Pick(t, left), childDepth),
            Build(Pick(x, right), Pick(t, right), childDepth));
    }

    /// <summary>多数派のラベル。同数なら先に現れたラベルを選ぶ。</summary>
    internal static string Majority(IReadOnlyList<string> labels) =>
        Counts(labels).Aggregate((best, next) => next.Value > best.Value ? next : best).Key;

    /// <summary>ラベルごとの件数を、ラベルが先に現れた順に並べて返す。</summary>
    private static Dictionary<string, int> Counts(IReadOnlyList<string> labels)
    {
        var counts = new Dictionary<string, int>(StringComparer.Ordinal);
        foreach (var label in labels)
        {
            counts[label] = counts.GetValueOrDefault(label) + 1;
        }

        return counts;
    }

    private static bool GoesLeft(Split split, Features features) =>
        features.Value(split.Feature) <= split.Threshold;

    private static IReadOnlyList<T> Pick<T>(IReadOnlyList<T> values, IReadOnlyList<int> positions) =>
        [.. positions.Select(i => values[i])];

    private static string Format(Tree tree, string indent) =>
        tree switch
        {
            Leaf leaf => indent + leaf.Label,
            Node node => string.Join(
                Environment.NewLine,
                $"{indent}{node.Split.Feature} <= {node.Split.Threshold.ToString("F4", CultureInfo.InvariantCulture)}",
                Format(node.Left, indent + "  "),
                $"{indent}{node.Split.Feature} > {node.Split.Threshold.ToString("F4", CultureInfo.InvariantCulture)}",
                Format(node.Right, indent + "  ")),
            _ => throw new ArgumentException($"知らない木です: {tree}", nameof(tree)),
        };
}
