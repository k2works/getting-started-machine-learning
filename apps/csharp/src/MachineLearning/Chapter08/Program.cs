namespace MachineLearning.Chapter08;

using System.Globalization;
using MachineLearning.Chapter02;
using MachineLearning.Dataset;

/// <summary>クラスの重みごとの評価結果を表示し、学習済みのパイプラインとモデルを保存して読み込む。</summary>
public static class Program
{
    /// <summary>学習済みのパイプラインとモデルの保存先（apps/csharp/model/ は .gitignore の対象）</summary>
    public const string ModelDirectory = "model";

    /// <summary>自作のパイプラインを保存する JSON のファイル名</summary>
    public const string PipelineFileName = "survived.json";

    /// <summary>ML.NET の FastTree を保存する zip のファイル名</summary>
    public const string MlNetFileName = "survived-mlnet.zip";

    private const double TestSize = 0.2;
    private const int Seed = 0;
    private const int MaxDepth = 5;

    private static readonly ClassWeight[] ClassWeights = [ClassWeight.None, ClassWeight.Balanced];

    /// <summary>年齢が分からない架空の乗客 2 人（1 等客室の女性と、3 等客室の男性）</summary>
    private static Table NewPassengers { get; } = SurvivedData.ToTable(
    [
        Passenger("1", "female", string.Empty, "0", "0", "50", "C"),
        Passenger("3", "male", string.Empty, "0", "0", "8", "S"),
    ]);

    public static void Run(TextWriter output) => Run(output, ModelDirectory);

    /// <summary>保存先を指定して実行する。テストから一時ディレクトリを渡すために使う。</summary>
    public static void Run(TextWriter output, string modelDirectory)
    {
        ArgumentNullException.ThrowIfNull(output);
        var rows = Table.Load(Path.Combine(DataDir.Current(), "Survived.csv")).Rows;
        var t = SurvivedData.Labels(rows);
        var split = Preprocessing.SplitTrainTest(rows, t, TestSize, Seed);
        var survivors = t.Count(label => label == 1);
        output.WriteLine($"データ件数: {rows.Count}（生存 {survivors}, 死亡 {rows.Count - survivors}）");
        output.WriteLine($"訓練データ: {split.XTrain.Count} 件, テストデータ: {split.XTest.Count} 件");

        var pipelines = ClassWeights.ToDictionary(
            classWeight => classWeight,
            classWeight => Pipeline.Build(MaxDepth, classWeight)
                .Fit(SurvivedData.ToTable(split.XTrain), split.TTrain));

        foreach (var classWeight in ClassWeights)
        {
            var pipeline = pipelines[classWeight];
            var result = Evaluation.Of(pipeline, split);
            var agreed = AgreementWithMlNet(pipeline, split, classWeight);
            output.WriteLine(
                $"classWeight={classWeight}: 訓練 {Format(result.TrainAccuracy)}, テスト {Format(result.TestAccuracy)}, "
                + $"生存者 {result.Survivors} 人中 {result.FoundSurvivors} 人を発見, "
                + $"ML.NET と一致 {agreed}/{split.XTest.Count}");
        }

        var balanced = pipelines[ClassWeight.Balanced];
        var pipelineFile = Path.Combine(modelDirectory, PipelineFileName);
        ModelFiles.Save(balanced, pipelineFile);
        output.WriteLine(
            $"架空の乗客の予測（{PipelineFileName}）: [{string.Join(", ", ModelFiles.Load(pipelineFile).Predict(NewPassengers))}]");

        var mlNetFile = Path.Combine(modelDirectory, MlNetFileName);
        MlNetAdapter.SaveFastTree(TrainMlNet(balanced, split, ClassWeight.Balanced), mlNetFile);
        var mlNetPredictions = MlNetAdapter.PredictFastTree(
            MlNetAdapter.LoadFastTree(mlNetFile), balanced.ToFeatures(NewPassengers));
        output.WriteLine($"架空の乗客の予測（{MlNetFileName}）: [{string.Join(", ", mlNetPredictions)}]");
    }

    /// <summary>自作の決定木と ML.NET の FastTree で、テストデータの予測が一致した件数。</summary>
    private static int AgreementWithMlNet(
        FittedPipeline pipeline, TrainTestSplit<Row, int> split, ClassWeight classWeight)
    {
        var mlNet = MlNetAdapter.PredictFastTree(
            TrainMlNet(pipeline, split, classWeight), pipeline.ToFeatures(SurvivedData.ToTable(split.XTest)));
        return pipeline.Predict(SurvivedData.ToTable(split.XTest)).Zip(mlNet).Count(pair => pair.First == pair.Second);
    }

    /// <summary>自作のパイプラインで前処理した訓練データで、行の重みを付けた FastTree を学習する。</summary>
    private static Microsoft.ML.ITransformer TrainMlNet(
        FittedPipeline pipeline, TrainTestSplit<Row, int> split, ClassWeight classWeight) =>
        MlNetAdapter.TrainFastTree(
            (int)Math.Pow(2, MaxDepth),
            pipeline.ToFeatures(SurvivedData.ToTable(split.XTrain)),
            split.TTrain,
            DecisionTreeClassifier.Weights(classWeight, split.TTrain));

    /// <summary>特徴量の列の順に並べた値から、乗客 1 人分の行を作る。空文字列は欠損値。</summary>
    private static Row Passenger(params string[] values)
    {
        var cells = new Dictionary<string, string>(StringComparer.Ordinal);
        for (var i = 0; i < values.Length; i++)
        {
            cells[SurvivedData.FeatureColumns[i]] = values[i];
        }

        return new Row(cells);
    }

    private static string Format(double value) => value.ToString("F3", CultureInfo.InvariantCulture);
}
