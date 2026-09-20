namespace MachineLearning.Tests.Chapter10;

using MachineLearning.Chapter02;
using MachineLearning.Chapter10;

public class RandomForestTests
{
    /// <summary>がく片幅と花弁幅を持つ、2 品種 10 件のデータ。</summary>
    internal static IReadOnlyList<Features> TwoSpeciesX { get; } = TwoSpecies(
        [0.5, 0.3, 0.6, 0.4, 0.5, 0.3, 0.6, 0.4, 0.5, 0.3],
        [0.1, 0.12, 0.14, 0.16, 0.18, 0.8, 0.82, 0.84, 0.86, 0.88]);

    internal static IReadOnlyList<string> TwoSpeciesT { get; } =
        [.. Enumerable.Repeat("setosa", 5), .. Enumerable.Repeat("virginica", 5)];

    /// <summary>がく片幅と花弁幅の 2 列を持つ行のリストを作る。</summary>
    internal static IReadOnlyList<Features> TwoSpecies(double[] sepalWidths, double[] petalWidths) =>
        [.. sepalWidths.Zip(petalWidths)
            .Select(pair => new Features(["がく片幅", "花弁幅"], [pair.First, pair.Second]))];

    [Fact(DisplayName = "サンプルごとに最も多い予測を選ぶ")]
    public void MajorityVote()
    {
        IReadOnlyList<IReadOnlyList<string>> votes =
        [
            ["setosa", "virginica"],
            ["setosa", "virginica"],
            ["versicolor", "setosa"],
        ];

        Assert.Equal(["setosa", "virginica"], RandomForest.MajorityVote(votes));
    }

    [Fact(DisplayName = "元のデータと同じ件数の行番号を重複を許して選ぶ")]
    public void BootstrapSample()
    {
        var rows = RandomForest.BootstrapSample(0, 100);

        Assert.Equal(100, rows.Count);
        Assert.All(rows, row => Assert.InRange(row, 0, 99));
        Assert.True(rows.Distinct().Count() < 100);
    }

    [Fact(DisplayName = "同じシードなら同じ行を選ぶ")]
    public void SameSeedSameRows()
    {
        Assert.Equal(RandomForest.BootstrapSample(42, 10), RandomForest.BootstrapSample(42, 10));
    }

    [Fact(DisplayName = "指定した数の特徴量を元の列の順で選ぶ")]
    public void ChoosesFeatures()
    {
        string[] features = ["a", "b", "c", "d"];

        var chosen = RandomForest.ChooseFeatures(0, 2, features);

        Assert.Equal(2, chosen.Count);
        Assert.Equal(chosen.Order(StringComparer.Ordinal), chosen);
        Assert.Equal(chosen, RandomForest.ChooseFeatures(0, 2, features));
    }

    [Fact(DisplayName = "指定した数だけ第 3 章の決定木を学習する")]
    public void LearnsGivenNumberOfTrees()
    {
        var forest = RandomForest.Learn(
            RandomForest.Settings.Default with { NEstimators = 5, MaxFeatures = 1 }, TwoSpeciesX, TwoSpeciesT);

        Assert.Equal(5, forest.Count);
    }

    [Fact(DisplayName = "各決定木は指定した数の特徴量だけを使う")]
    public void EachTreeUsesGivenNumberOfFeatures()
    {
        var forest = RandomForest.Learn(
            RandomForest.Settings.Default with { NEstimators = 5, MaxFeatures = 1 }, TwoSpeciesX, TwoSpeciesT);

        Assert.All(forest, fitted => Assert.Single(fitted.Columns));
    }

    [Fact(DisplayName = "決定木の多数決で予測する")]
    public void PredictsByMajorityVote()
    {
        var forest = RandomForest.Learn(
            RandomForest.Settings.Default with { NEstimators = 25 }, TwoSpeciesX, TwoSpeciesT);

        var newX = TwoSpecies([0.4, 0.4], [0.13, 0.83]);

        Assert.Equal(["setosa", "virginica"], RandomForest.Predict(forest, newX));
    }

    [Fact(DisplayName = "同じシードなら同じ森になる")]
    public void SameSeedSameForest()
    {
        var settings = RandomForest.Settings.Default with { NEstimators = 5, MaxFeatures = 1, Seed = 7 };

        var first = RandomForest.Learn(settings, TwoSpeciesX, TwoSpeciesT);
        var second = RandomForest.Learn(settings, TwoSpeciesX, TwoSpeciesT);

        // record の既定の比較はリストを参照で比べるので、成分ごとに比べる（第 2 章の Features と同じ落とし穴）
        Assert.Equal(first.Select(fitted => fitted.Columns), second.Select(fitted => fitted.Columns));
        Assert.Equal(first.Select(fitted => fitted.Rows), second.Select(fitted => fitted.Rows));
        Assert.Equal(first.Select(fitted => fitted.Tree), second.Select(fitted => fitted.Tree));
    }
}
