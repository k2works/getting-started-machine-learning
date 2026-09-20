namespace MachineLearning.Tests.Chapter09;

using MachineLearning.Chapter02;
using MachineLearning.Chapter09;

public class PolynomialFeaturesTests
{
    [Fact(DisplayName = "同じ列の組も含めて、列の組を重複なく作る")]
    public void PairsWithReplacement() =>
        Assert.Equal(
            ["a^2", "a b", "b^2"],
            PolynomialFeatures.PairsWithReplacement(["a", "b"]).Select(term => term.Name));

    [Fact(DisplayName = "元の列と 2 次の項の列を持つ特徴量を作る")]
    public void ExpandsToSecondOrder()
    {
        IReadOnlyList<Features> x = [new Features(["a", "b", "c"], [2.0, 3.0, 9.0])];

        var expanded = PolynomialFeatures.Expand(x, ["a", "b"]);

        Assert.Equal(["a", "b", "a^2", "a b", "b^2"], expanded[0].Columns);
        Assert.Equal([2.0, 3.0, 4.0, 6.0, 9.0], expanded[0].Values);
    }

    [Fact(DisplayName = "指定した列だけを、その順に選ぶ")]
    public void SelectsColumnsInOrder()
    {
        IReadOnlyList<Features> x = [new Features(["a", "b", "c"], [1.0, 2.0, 3.0])];

        var selected = PolynomialFeatures.Select(x, ["c", "a"]);

        Assert.Equal(new Features(["c", "a"], [3.0, 1.0]), selected[0]);
    }
}
