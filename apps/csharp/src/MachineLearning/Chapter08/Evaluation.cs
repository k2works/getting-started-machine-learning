namespace MachineLearning.Chapter08;

using MachineLearning.Chapter02;

/// <summary>訓練データとテストデータの正解率と、テストデータの生存者のうち生存と予測できた人数。</summary>
public sealed record Evaluation(double TrainAccuracy, double TestAccuracy, int FoundSurvivors, int Survivors)
{
    private const int Survived = 1;

    /// <summary>学習済みのパイプラインを、訓練データとテストデータで評価する。</summary>
    public static Evaluation Of(FittedPipeline pipeline, TrainTestSplit<Row, int> split)
    {
        ArgumentNullException.ThrowIfNull(pipeline);
        ArgumentNullException.ThrowIfNull(split);
        var predictions = pipeline.Predict(SurvivedData.ToTable(split.XTest));
        return new Evaluation(
            Accuracy(pipeline.Predict(SurvivedData.ToTable(split.XTrain)), split.TTrain),
            Accuracy(predictions, split.TTest),
            predictions.Zip(split.TTest).Count(pair => pair.First == Survived && pair.Second == Survived),
            split.TTest.Count(label => label == Survived));
    }

    /// <summary>予測が正解ラベルと一致した割合。</summary>
    public static double Accuracy(IReadOnlyList<int> predictions, IReadOnlyList<int> labels)
    {
        ArgumentNullException.ThrowIfNull(predictions);
        ArgumentNullException.ThrowIfNull(labels);
        return (double)predictions.Zip(labels).Count(pair => pair.First == pair.Second) / labels.Count;
    }
}
