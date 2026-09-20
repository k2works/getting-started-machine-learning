namespace MachineLearning.Tests.Chapter10;

using MachineLearning.Chapter02;
using MachineLearning.Chapter10;

public class MlNetClassifierTests
{
    private static readonly TrainTestSplit<Features, string> TwoSpeciesSplit = new(
        RandomForestTests.TwoSpeciesX,
        RandomForestTests.TwoSpecies([0.4, 0.4], [0.13, 0.83]),
        RandomForestTests.TwoSpeciesT,
        ["setosa", "virginica"]);

    [Fact(DisplayName = "ML.NET のロジスティック回帰とランダムフォレストも同じインターフェースで評価できる")]
    public void SameInterfaceForMlNet()
    {
        IClassifier[] classifiers =
        [
            MlNetClassifier.LbfgsMaximumEntropy(1.0f, 1.0f),
            MlNetClassifier.FastForest(10, 1),
        ];

        var scores = classifiers.Select(classifier => classifier.Evaluate(TwoSpeciesSplit));

        Assert.Equal(Enumerable.Repeat(new Score(1.0, 1.0), 2), scores);
    }
}
