namespace MachineLearning.Tests.Chapter10;

using MachineLearning.Chapter02;
using MachineLearning.Chapter10;

public class LogisticRegressionTests
{
    /// <summary>花弁幅だけを特徴量に持つ行のリストを作る。</summary>
    internal static IReadOnlyList<Features> ByPetalWidth(params double[] values) =>
        [.. values.Select(value => new Features(["花弁幅"], [value]))];

    [Fact(DisplayName = "値がすべて同じなら確率は均等になる")]
    public void UniformScores()
    {
        Assert.Equal([0.25, 0.25, 0.25, 0.25], LogisticRegression.Softmax([0.0, 0.0, 0.0, 0.0]));
    }

    [Fact(DisplayName = "値の差が指数の比になる")]
    public void RatioOfExponentials()
    {
        var probabilities = LogisticRegression.Softmax([0.0, Math.Log(2.0)]);

        Assert.Equal(1.0 / 3.0, probabilities[0], 12);
        Assert.Equal(2.0 / 3.0, probabilities[1], 12);
    }

    [Fact(DisplayName = "大きな値でもあふれずに確率を求める")]
    public void NoOverflow()
    {
        Assert.Equal([0.5, 0.5], LogisticRegression.Softmax([1000.0, 1000.0]));
    }

    [Fact(DisplayName = "1 種類のラベルだけを学習するとそのラベルを予測する")]
    public void SingleLabel()
    {
        var model = LogisticModel.Learn(LogisticSettings.Default, ByPetalWidth(0.1, 0.2), ["setosa", "setosa"]);

        Assert.Equal(["setosa", "setosa"], model.Predict(ByPetalWidth(0.15, 0.9)));
    }

    [Fact(DisplayName = "2 種類のラベルを境界の左右で予測する")]
    public void TwoLabels()
    {
        var model = LogisticModel.Learn(
            LogisticSettings.Default,
            ByPetalWidth(0.1, 0.2, 0.8, 0.9),
            ["setosa", "setosa", "virginica", "virginica"]);

        Assert.Equal(["setosa", "virginica"], model.Predict(ByPetalWidth(0.15, 0.85)));
    }

    [Fact(DisplayName = "3 種類のラベルを 2 つの特徴量から予測する")]
    public void ThreeLabels()
    {
        double[] lengths = [0.1, 0.2, 0.5, 0.6, 0.5, 0.6];
        double[] widths = [0.1, 0.2, 0.1, 0.2, 0.8, 0.9];
        var x = lengths.Zip(widths)
            .Select(pair => new Features(["花弁長さ", "花弁幅"], [pair.First, pair.Second]))
            .ToList();
        string[] t = ["setosa", "setosa", "versicolor", "versicolor", "virginica", "virginica"];

        Assert.Equal(t, LogisticModel.Learn(LogisticSettings.Default, x, t).Predict(x));
    }

    [Fact(DisplayName = "学習を繰り返すと損失が小さくなる")]
    public void LossDecreases()
    {
        var model = LogisticModel.Learn(
            LogisticSettings.Default with { Epochs = 100 },
            ByPetalWidth(0.1, 0.2, 0.8, 0.9),
            ["setosa", "setosa", "virginica", "virginica"]);

        Assert.Equal(100, model.Losses.Count);
        Assert.True(model.Losses[^1] < model.Losses[0]);
    }
}
