namespace MachineLearning.Chapter08;

using MachineLearning.Chapter02;

/// <summary>クラスの重みを付けられる決定木の分類器。Fit で学習済みの木を返す。MaxDepth が負なら深さの上限なし。</summary>
public sealed record DecisionTreeClassifier(int MaxDepth, ClassWeight ClassWeight)
{
    /// <summary>深さを制限しないことを表す値</summary>
    public const int Unlimited = -1;

    /// <summary>訓練データから木を作る。</summary>
    public FittedDecisionTree Fit(IReadOnlyList<Features> x, IReadOnlyList<int> t) =>
        new(WeightedTrees.Build(x, t, Weights(this.ClassWeight, t), this.MaxDepth));

    /// <summary>正解ラベルから、1 件ごとの重みを求める。</summary>
    public static IReadOnlyList<double> Weights(ClassWeight classWeight, IReadOnlyList<int> t)
    {
        ArgumentNullException.ThrowIfNull(t);
        return classWeight switch
        {
            ClassWeight.None => [.. t.Select(_ => 1.0)],
            ClassWeight.Balanced => WeightedTrees.BalancedWeights(t),
            _ => throw new ArgumentOutOfRangeException(nameof(classWeight)),
        };
    }
}

/// <summary>学習済みの決定木。</summary>
public sealed record FittedDecisionTree(TreeNode Root)
{
    /// <summary>特徴量ごとのラベルを予測する。</summary>
    public IReadOnlyList<int> Predict(IReadOnlyList<Features> x)
    {
        ArgumentNullException.ThrowIfNull(x);
        return [.. x.Select(features => WeightedTrees.PredictOne(this.Root, features))];
    }
}
