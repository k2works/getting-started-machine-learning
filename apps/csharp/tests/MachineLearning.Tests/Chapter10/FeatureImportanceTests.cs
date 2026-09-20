namespace MachineLearning.Tests.Chapter10;

using MachineLearning.Chapter02;
using MachineLearning.Chapter03;
using MachineLearning.Chapter10;

public class FeatureImportanceTests
{
    /// <summary>2 つの特徴量の値のリストから、行のリストを作る。</summary>
    private static IReadOnlyList<Features> RowsOf(
        (string Name, double[] Values) first, (string Name, double[] Values) second) =>
        [.. first.Values.Zip(second.Values)
            .Select(pair => new Features([first.Name, second.Name], [pair.First, pair.Second]))];

    [Fact(DisplayName = "分割しない木はすべての特徴量の重要度が 0")]
    public void LeafHasNoImportance()
    {
        var x = RowsOf(("がく片幅", [0.3, 0.5]), ("花弁幅", [0.1, 0.2]));

        var importances = FeatureImportance.TreeImportances(new Leaf("setosa"), x, ["setosa", "setosa"]);

        Assert.Equal(0.0, importances["がく片幅"]);
        Assert.Equal(0.0, importances["花弁幅"]);
    }

    [Fact(DisplayName = "1 回だけ分割する木は分割に使った特徴量の重要度が 1")]
    public void SingleSplitHasImportanceOne()
    {
        var x = RowsOf(("がく片幅", [0.3, 0.5, 0.4, 0.6]), ("花弁幅", [0.1, 0.2, 0.8, 0.9]));
        string[] t = ["setosa", "setosa", "virginica", "virginica"];

        var tree = DecisionTree.Unlimited().Fit(x, t).Tree!;

        var importances = FeatureImportance.TreeImportances(tree, x, t);

        Assert.Equal(0.0, importances["がく片幅"]);
        Assert.Equal(1.0, importances["花弁幅"]);
    }

    [Fact(DisplayName = "分割で減った不純度を件数で重み付けして割合にする")]
    public void WeightsImpurityDecreaseByCount()
    {
        var x = RowsOf(("花弁長さ", [0.1, 0.2, 0.3, 0.8, 0.7, 0.9]), ("花弁幅", [0.1, 0.1, 0.2, 0.2, 0.9, 0.9]));
        string[] t = ["setosa", "setosa", "setosa", "versicolor", "virginica", "virginica"];

        var importances = FeatureImportance.TreeImportances(DecisionTree.Unlimited().Fit(x, t).Tree!, x, t);

        Assert.Equal(7.0 / 11.0, importances["花弁長さ"], 12);
        Assert.Equal(4.0 / 11.0, importances["花弁幅"], 12);
    }

    [Fact(DisplayName = "木が 1 本なら学習に使った行でのその木の重要度と一致する")]
    public void SingleTreeForest()
    {
        var x = RowsOf(
            ("がく片幅", [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4]),
            ("花弁幅", [0.1, 0.12, 0.14, 0.16, 0.8, 0.82, 0.84, 0.86]));
        string[] t = [.. Enumerable.Repeat("setosa", 4), .. Enumerable.Repeat("virginica", 4)];

        var forest = RandomForest.Learn(
            RandomForest.Settings.Default with { NEstimators = 1, MaxFeatures = 2 }, x, t);
        var fitted = Assert.Single(forest);

        var expected = FeatureImportance.TreeImportances(
            fitted.Tree,
            [.. fitted.Rows.Select(row => x[row])],
            [.. fitted.Rows.Select(row => t[row])]);

        Assert.Equal(expected, FeatureImportance.ForestImportances(forest, x, t));
    }
}
