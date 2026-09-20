namespace MachineLearning.Chapter10;

using MachineLearning.Chapter02;
using MachineLearning.Chapter03;

/// <summary>決定木とランダムフォレストの特徴量の重要度。</summary>
public static class FeatureImportance
{
    /// <summary>決定木 1 本の特徴量の重要度。</summary>
    public static IReadOnlyDictionary<string, double> TreeImportances(
        Tree tree, IReadOnlyList<Features> x, IReadOnlyList<string> t)
    {
        ArgumentNullException.ThrowIfNull(x);
        var decreases = new Dictionary<string, double>(StringComparer.Ordinal);
        Collect(tree, x, t, decreases);
        return Normalize(x[0].Columns.ToDictionary(
            column => column, decreases.GetValueOrDefault, StringComparer.Ordinal));
    }

    /// <summary>
    /// ランダムフォレストの特徴量の重要度。
    /// 木ごとの重要度（学習に使ったブートストラップ標本で計算）を平均し、割合にする。
    /// </summary>
    public static IReadOnlyDictionary<string, double> ForestImportances(
        IReadOnlyList<FittedTree> forest, IReadOnlyList<Features> x, IReadOnlyList<string> t)
    {
        ArgumentNullException.ThrowIfNull(forest);
        ArgumentNullException.ThrowIfNull(x);
        var perTree = forest.Select(fitted =>
        {
            var (sampleX, sampleT) = RandomForest.SampleOf(fitted.Rows, fitted.Columns, x, t);
            return TreeImportances(fitted.Tree, sampleX, sampleT);
        }).ToList();

        return Normalize(x[0].Columns.ToDictionary(
            column => column,
            column => perTree.Average(importances => importances.GetValueOrDefault(column)),
            StringComparer.Ordinal));
    }

    /// <summary>
    /// 学習に使ったデータをもう一度木に流して、
    /// 節ごとに「分割に使った特徴量と、件数で重み付けした不純度の減少量」を集める。
    /// </summary>
    private static void Collect(
        Tree tree, IReadOnlyList<Features> x, IReadOnlyList<string> t, Dictionary<string, double> decreases)
    {
        if (tree is not Node node)
        {
            return;
        }

        var split = node.Split;
        var left = Enumerable.Range(0, x.Count).Where(i => x[i].Value(split.Feature) <= split.Threshold).ToList();
        var right = Enumerable.Range(0, x.Count).Where(i => x[i].Value(split.Feature) > split.Threshold).ToList();
        decreases[split.Feature] =
            decreases.GetValueOrDefault(split.Feature) + (t.Count * (DecisionTrees.Gini(t) - split.Impurity));
        Collect(node.Left, Pick(x, left), Pick(t, left), decreases);
        Collect(node.Right, Pick(x, right), Pick(t, right), decreases);
    }

    /// <summary>合計が 1 になるように割合にする。合計が 0 ならそのまま返す。</summary>
    private static Dictionary<string, double> Normalize(Dictionary<string, double> totals)
    {
        var total = totals.Values.Sum();
        return total == 0.0
            ? totals
            : totals.ToDictionary(pair => pair.Key, pair => pair.Value / total, StringComparer.Ordinal);
    }

    private static IReadOnlyList<T> Pick<T>(IReadOnlyList<T> values, IReadOnlyList<int> positions) =>
        [.. positions.Select(i => values[i])];
}
