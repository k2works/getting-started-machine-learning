namespace MachineLearning.Tests.Chapter03;

using MachineLearning.Chapter02;
using MachineLearning.Chapter03;

internal static class Samples
{
    /// <summary>1 列だけの特徴量を値の数だけ作る。</summary>
    internal static IReadOnlyList<Features> Column(string name, params double[] values) =>
        [.. values.Select(value => new Features([name], [value]))];

    /// <summary>3 品種のうち、境界の右側に versicolor 2 件と virginica 1 件が残るデータ。</summary>
    internal static IReadOnlyList<Features> ThreeSpeciesX() => Column("花弁幅", 0.1, 0.2, 0.3, 0.5, 0.6, 0.9);

    internal static IReadOnlyList<string> ThreeSpeciesT() =>
        ["setosa", "setosa", "setosa", "versicolor", "versicolor", "virginica"];
}

public class GiniTests
{
    [Fact(DisplayName = "1 種類のラベルだけならジニ不純度は 0")]
    public void Pure() => Assert.Equal(0.0, DecisionTrees.Gini(["setosa", "setosa", "setosa"]));

    [Fact(DisplayName = "2 種類のラベルが半分ずつならジニ不純度は 0.5")]
    public void Half() => Assert.Equal(0.5, DecisionTrees.Gini(["setosa", "virginica"]));

    [Fact(DisplayName = "3 種類のラベルが同じ数ならジニ不純度は 3 分の 2")]
    public void Three() => Assert.Equal(2.0 / 3, DecisionTrees.Gini(["setosa", "versicolor", "virginica"]), 12);
}

public class BestSplitTests
{
    [Fact(DisplayName = "ラベルを完全に分けられる境界を見つける")]
    public void Separates()
    {
        var split = DecisionTrees.BestSplit(
            Samples.Column("花弁幅", 0.1, 0.2, 0.7, 0.8),
            ["setosa", "setosa", "virginica", "virginica"]);

        Assert.NotNull(split);
        Assert.Equal("花弁幅", split.Feature);
        Assert.Equal(0.45, split.Threshold, 12);
        Assert.Equal(0.0, split.Impurity, 12);
    }

    [Fact(DisplayName = "複数の特徴量から不純度が最も小さくなる特徴量と境界を選ぶ")]
    public void ChoosesBestFeature()
    {
        List<string> columns = ["がく片長さ", "花弁長さ"];
        List<Features> x =
        [
            new(columns, [0.1, 0.2]),
            new(columns, [0.3, 0.1]),
            new(columns, [0.2, 0.9]),
            new(columns, [0.4, 0.6]),
        ];

        var split = DecisionTrees.BestSplit(x, ["setosa", "setosa", "virginica", "virginica"]);

        Assert.NotNull(split);
        Assert.Equal("花弁長さ", split.Feature);
        Assert.Equal(0.4, split.Threshold, 12);
    }

    [Fact(DisplayName = "ラベルが 1 種類なら分割しない")]
    public void NoSplitForPureLabels() =>
        Assert.Null(DecisionTrees.BestSplit(Samples.Column("花弁幅", 0.1, 0.2, 0.7), ["setosa", "setosa", "setosa"]));
}

public class DecisionTreeTests
{
    [Fact(DisplayName = "1 種類のラベルだけを学習するとそのラベルを予測する")]
    public void SingleLabel()
    {
        var model = DecisionTree.Unlimited().Fit(Samples.Column("花弁幅", 0.1, 0.2), ["setosa", "setosa"]);

        Assert.Equal(["setosa", "setosa"], model.Predict(Samples.Column("花弁幅", 0.15, 0.9)));
    }

    [Fact(DisplayName = "境界の左右で異なるラベルを予測する")]
    public void LeftAndRight()
    {
        var model = DecisionTree.Unlimited()
            .Fit(Samples.Column("花弁幅", 0.1, 0.2, 0.7, 0.8), ["setosa", "setosa", "virginica", "virginica"]);

        Assert.Equal(["setosa", "virginica"], model.Predict(Samples.Column("花弁幅", 0.15, 0.75)));
    }

    [Fact(DisplayName = "深さを制限しなければすべての訓練データを分け切る")]
    public void UnlimitedDepth()
    {
        var model = DecisionTree.Unlimited().Fit(Samples.ThreeSpeciesX(), Samples.ThreeSpeciesT());

        Assert.Equal(Samples.ThreeSpeciesT(), model.Predict(Samples.ThreeSpeciesX()));
    }

    [Fact(DisplayName = "深さを 1 に制限すると境界の先は多数派のラベルを予測する")]
    public void DepthOne()
    {
        var model = DecisionTree.WithMaxDepth(1).Fit(Samples.ThreeSpeciesX(), Samples.ThreeSpeciesT());

        Assert.Equal(["setosa", "versicolor"], model.Predict(Samples.Column("花弁幅", 0.2, 0.95)));
    }

    [Fact(DisplayName = "学習する前に予測するとエラーになる")]
    public void PredictBeforeFit()
    {
        var error = Assert.Throws<InvalidOperationException>(
            () => DecisionTree.Unlimited().Predict(Samples.Column("花弁幅", 0.1)));

        Assert.Equal("Fit で学習してから Predict を呼んでください", error.Message);
    }
}

public class FormatTests
{
    [Fact(DisplayName = "葉だけの木はラベルを表示する")]
    public void LeafOnly() => Assert.Equal("setosa", DecisionTrees.Format(new Leaf("setosa")));

    [Fact(DisplayName = "節は条件ごとに字下げして表示する")]
    public void NodeTree()
    {
        Tree tree = new Node(
            new Split("花弁幅", 0.4, 0.0),
            new Leaf("setosa"),
            new Node(new Split("花弁長さ", 0.75, 0.0), new Leaf("versicolor"), new Leaf("virginica")));

        Assert.Equal(
            string.Join(
                Environment.NewLine,
                "花弁幅 <= 0.4000",
                "  setosa",
                "花弁幅 > 0.4000",
                "  花弁長さ <= 0.7500",
                "    versicolor",
                "  花弁長さ > 0.7500",
                "    virginica"),
            DecisionTrees.Format(tree));
    }
}
