namespace MachineLearning.Chapter03;

using MachineLearning.Chapter02;

/// <summary>自作の決定木の分類器。Fit で学習してから Predict で予測する。</summary>
public sealed class DecisionTree
{
    private const int UnlimitedDepth = -1;

    private readonly int maxDepth;

    private DecisionTree(int maxDepth) => this.maxDepth = maxDepth;

    /// <summary>学習した木。学習する前は null。</summary>
    public Tree? Tree { get; private set; }

    /// <summary>深さを制限しない決定木。</summary>
    public static DecisionTree Unlimited() => new(UnlimitedDepth);

    /// <summary>深さの上限を指定した決定木。</summary>
    public static DecisionTree WithMaxDepth(int maxDepth) =>
        maxDepth < 0
            ? throw new ArgumentOutOfRangeException(nameof(maxDepth), "深さの上限は 0 以上にしてください")
            : new DecisionTree(maxDepth);

    /// <summary>訓練データから木を作る。</summary>
    public DecisionTree Fit(IReadOnlyList<Features> x, IReadOnlyList<string> t)
    {
        this.Tree = DecisionTrees.Build(x, t, this.maxDepth);
        return this;
    }

    /// <summary>特徴量ごとのラベルを予測する。</summary>
    public IReadOnlyList<string> Predict(IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(x);
        var tree = this.Tree ?? throw new InvalidOperationException("Fit で学習してから Predict を呼んでください");
        return [.. x.Select(features => DecisionTrees.PredictOne(tree, features))];
    }
}
