namespace MachineLearning.Tests.Chapter10;

using MachineLearning.Chapter02;
using MachineLearning.Chapter10;
using MachineLearning.Dataset;

public class IrisDataTests
{
    private readonly string csvFile = Path.Combine(DataDir.Current(), "iris.csv");

    [Fact(DisplayName = "ロジスティック回帰はテストデータの 45 件中 39 件を正しく分類する")]
    public void LogisticRegressionAccuracy()
    {
        this.RequireData();

        var score = new LogisticRegression(LogisticSettings.Default).Evaluate(this.IrisSplit());

        Assert.Equal(39.0 / 45, score.Test, 12);
    }

    [Fact(DisplayName = "ランダムフォレストは訓練データを分け切りテストデータの 45 件中 40 件を正しく分類する")]
    public void RandomForestAccuracy()
    {
        this.RequireData();

        var score = new RandomForest(RandomForest.Settings.Default with { NEstimators = 100 })
            .Evaluate(this.IrisSplit());

        Assert.Equal(1.0, score.Train);
        Assert.Equal(40.0 / 45, score.Test, 12);
    }

    [Fact(DisplayName = "ML.NET のロジスティック回帰は正則化を 0 にすると自作と同じ正解率になる")]
    public void MlNetMatchesOwnLogisticRegression()
    {
        this.RequireData();

        Assert.Equal(
            new LogisticRegression(LogisticSettings.Default).Evaluate(this.IrisSplit()),
            MlNetClassifier.LbfgsMaximumEntropy(0.0f, 0.0f).Evaluate(this.IrisSplit()));
    }

    [Fact(DisplayName = "ML.NET の FastForest は葉の最小件数を 1 にすると訓練データを分け切る")]
    public void MlNetFastForestFitsTrainingData()
    {
        this.RequireData();

        var score = MlNetClassifier.FastForest(100, 1).Evaluate(this.IrisSplit());

        Assert.Equal(1.0, score.Train);
        Assert.Equal(41.0 / 45, score.Test, 12);
    }

    [Fact(DisplayName = "実行するとモデルごとの正解率とランダムフォレストの重要度を表示する")]
    public void PrintsSummary()
    {
        this.RequireData();

        using var output = new StringWriter();
        MachineLearning.Chapter10.Program.Run(output);

        Assert.Equal(
            string.Join(
                Environment.NewLine,
                "モデル\t訓練データ\tテストデータ",
                "決定木（深さ 2）\t0.9619\t0.8889",
                "ロジスティック回帰\t0.9524\t0.8667",
                "ランダムフォレスト（100 本）\t1.0000\t0.8889",
                "ランダムフォレスト（100 本・深さ 2）\t0.9524\t0.8889",
                "ML.NET LbfgsMaximumEntropy\t0.9238\t0.8444",
                "ML.NET FastForest（100 本）\t0.9619\t0.8889",
                string.Empty,
                "ランダムフォレスト（100 本）の特徴量の重要度:",
                "がく片長さ\t0.2008",
                "がく片幅\t0.1075",
                "花弁長さ\t0.2243",
                "花弁幅\t0.4674") + Environment.NewLine,
            output.ToString());
    }

    private void RequireData() =>
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");

    private TrainTestSplit<Features, string> IrisSplit() => Preprocessing.PrepareIris(this.csvFile, 0.3, 0);
}
