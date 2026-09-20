namespace MachineLearning.Tests.Chapter10;

using MachineLearning.Chapter02;
using MachineLearning.Chapter03;
using MachineLearning.Chapter10;

public class ClassifierTests
{
    internal static TrainTestSplit<Features, string> SmallSplit { get; } = new(
        LogisticRegressionTests.ByPetalWidth(0.1, 0.2, 0.8, 0.9),
        LogisticRegressionTests.ByPetalWidth(0.15, 0.25),
        ["setosa", "setosa", "virginica", "virginica"],
        ["setosa", "setosa"]);

    [Fact(DisplayName = "学習させてから訓練データとテストデータの正解率を求める")]
    public void EvaluatesTrainAndTest()
    {
        Assert.Equal(new Score(0.5, 1.0), new AlwaysSetosa().Evaluate(SmallSplit));
    }

    [Fact(DisplayName = "第 3 章の決定木と自作のモデルを同じインターフェースで評価できる")]
    public void SameInterfaceForEveryModel()
    {
        IClassifier[] classifiers =
        [
            DecisionTreeClassifier.WithMaxDepth(1),
            new LogisticRegression(LogisticSettings.Default),
            new RandomForest(RandomForest.Settings.Default with { NEstimators = 5, MaxFeatures = 1 }),
        ];

        var scores = classifiers.Select(classifier => classifier.Evaluate(SmallSplit));

        Assert.Equal(Enumerable.Repeat(new Score(1.0, 1.0), 3), scores);
    }

    /// <summary>何を学習しても setosa と予測する分類器。</summary>
    private sealed class AlwaysSetosa : IClassifier
    {
        public Func<IReadOnlyList<Features>, IReadOnlyList<string>> Fit(
            IReadOnlyList<Features> x, IReadOnlyList<string> t) =>
            newX => [.. newX.Select(_ => "setosa")];
    }
}
