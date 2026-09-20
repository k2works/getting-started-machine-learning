namespace MachineLearning.Tests.Chapter08;

using MachineLearning.Chapter02;
using MachineLearning.Chapter03;
using MachineLearning.Chapter08;
using Features = MachineLearning.Chapter02.Features;

public class WeightedTreesTests
{
    private static readonly string[] Columns = ["x"];

    [Fact(DisplayName = "重みがすべて 1 なら重み付きのジニ不純度は第 3 章のジニ不純度と同じ")]
    public void WeightedGiniWithEqualWeights()
    {
        Assert.Equal(DecisionTrees.Gini(["0", "0", "1", "1"]), WeightedTrees.WeightedGini([0, 0, 1, 1], [1, 1, 1, 1]), 12);
        Assert.Equal(0.0, WeightedTrees.WeightedGini([1, 1], [1, 1]));
    }

    [Fact(DisplayName = "重みを付けると少ないほうのクラスの割合が大きくなる")]
    public void WeightedGiniWithWeights()
    {
        // 重み 3 のラベル 1 が 1 件、重み 1 のラベル 0 が 3 件。割合はどちらも 0.5 になる
        Assert.Equal(0.5, WeightedTrees.WeightedGini([1, 0, 0, 0], [3, 1, 1, 1]), 12);
    }

    [Fact(DisplayName = "balanced の重みはクラスごとの重みの合計をそろえる")]
    public void BalancedWeights()
    {
        var weights = WeightedTrees.BalancedWeights([0, 0, 0, 1]);

        Assert.Equal([4.0 / 6, 4.0 / 6, 4.0 / 6, 2.0], weights);
        Assert.Equal(2.0, weights.Where((_, i) => i < 3).Sum(), 12);
    }

    [Fact(DisplayName = "重みの合計が最も大きいラベルを選ぶ")]
    public void WeightedMajority()
    {
        Assert.Equal(0, WeightedTrees.WeightedMajority([0, 0, 1], [1, 1, 1]));
        Assert.Equal(1, WeightedTrees.WeightedMajority([0, 0, 1], [1, 1, 3]));
    }

    [Fact(DisplayName = "重み付けなしの木は第 3 章の決定木と同じ形になる")]
    public void SameTreeAsChapter03WithoutWeights()
    {
        var x = new List<Features>
        {
            new(Columns, [1.0]),
            new(Columns, [2.0]),
            new(Columns, [3.0]),
            new(Columns, [4.0]),
        };

        var tree = new DecisionTreeClassifier(1, ClassWeight.None).Fit(x, [0, 0, 1, 1]).Root;

        Assert.Equal(new SplitNode("x", 2.5, new LeafNode(0), new LeafNode(1)), tree);
    }

    [Fact(DisplayName = "重みを付けると少ないほうのクラスを予測する葉になる")]
    public void WeightsChangePrediction()
    {
        var x = new List<Features>
        {
            new(Columns, [1.0]),
            new(Columns, [2.0]),
            new(Columns, [3.0]),
            new(Columns, [4.0]),
            new(Columns, [5.0]),
        };
        var t = new List<int> { 0, 1, 0, 0, 0 };

        // 同じ分割（x <= 2.5）を選ぶが、左の葉のラベルが重みで変わる
        Assert.Equal([0], new DecisionTreeClassifier(1, ClassWeight.None).Fit(x, t).Predict([x[0]]));
        Assert.Equal([1], new DecisionTreeClassifier(1, ClassWeight.Balanced).Fit(x, t).Predict([x[0]]));
    }

    [Fact(DisplayName = "深さの上限を超えて分割しない")]
    public void StopsAtMaxDepth()
    {
        var x = new List<Features>
        {
            new(Columns, [1.0]),
            new(Columns, [2.0]),
            new(Columns, [3.0]),
            new(Columns, [4.0]),
        };

        var tree = new DecisionTreeClassifier(0, ClassWeight.None).Fit(x, [0, 1, 0, 1]).Root;

        Assert.Equal(new LeafNode(0), tree);
    }
}
