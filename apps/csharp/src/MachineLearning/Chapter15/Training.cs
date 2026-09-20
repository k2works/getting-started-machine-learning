namespace MachineLearning.Chapter15;

using MachineLearning.Chapter02;
using MachineLearning.Chapter07;
using MachineLearning.Chapter08;
using MachineLearning.Dataset;

/// <summary>第 7・8 章と同じ条件でモデルを学習し、置き場に保存する。</summary>
public static class Training
{
    private const double TestSize = 0.2;
    private const int Seed = 0;
    private const int MaxDepth = 5;

    public static void TrainAndSaveModels(string dataDirectory, FileModelStore store)
    {
        ArgumentNullException.ThrowIfNull(store);
        var cinema = Cinema.Prepare(Path.Combine(dataDirectory, "cinema.csv"), TestSize, Seed);
        store.SaveSalesModel(LinearRegression.Fit(cinema.XTrain, cinema.TTrain));

        var rows = Table.Load(Path.Combine(dataDirectory, "Survived.csv")).Rows;
        var split = Preprocessing.SplitTrainTest(rows, SurvivedData.Labels(rows), TestSize, Seed);
        var pipeline = Pipeline.Build(MaxDepth, ClassWeight.Balanced)
            .Fit(SurvivedData.ToTable(split.XTrain), split.TTrain);
        store.SaveSurvivalModel(pipeline);
    }
}
