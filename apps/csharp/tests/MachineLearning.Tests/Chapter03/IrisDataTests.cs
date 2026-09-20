namespace MachineLearning.Tests.Chapter03;

using MachineLearning.Chapter01;
using MachineLearning.Chapter02;
using MachineLearning.Chapter03;
using MachineLearning.Dataset;
using Features = MachineLearning.Chapter02.Features;

public class IrisDataTests
{
    private readonly string csvFile = Path.Combine(DataDir.Current(), "iris.csv");

    [Fact(DisplayName = "深さ 2 の決定木はテストデータの 45 件中 40 件を正しく分類する")]
    public void DepthTwoAccuracy()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");

        var split = this.IrisSplit();

        var predictions = DecisionTree.WithMaxDepth(2).Fit(split.XTrain, split.TTrain).Predict(split.XTest);

        Assert.Equal(40.0 / 45, KinokoTakenoko.Accuracy(predictions, split.TTest), 12);
    }

    [Theory(DisplayName = "深さごとに ML.NET の FastTree と一致する件数を確かめる")]
    [InlineData(1, 32)]
    [InlineData(2, 45)]
    [InlineData(3, 43)]
    [InlineData(4, 44)]
    [InlineData(5, 44)]
    public void AgreementWithMlNet(int maxDepth, int expected)
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");

        var split = this.IrisSplit();
        var mine = DecisionTree.WithMaxDepth(maxDepth).Fit(split.XTrain, split.TTrain).Predict(split.XTest);
        var mlNet = MlNetAdapter.TrainFastTree((int)Math.Pow(2, maxDepth), split.XTrain, split.TTrain)(split.XTest);

        var agreed = mine.Zip(mlNet).Count(pair => string.Equals(pair.First, pair.Second, StringComparison.Ordinal));

        Assert.Equal(expected, agreed);
    }

    [Fact(DisplayName = "実行すると深さごとの正解率と深さ 2 の決定木を表示する")]
    public void PrintsSummary()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ iris.csv が配置されていない（gulp data:setup）");

        using var output = new StringWriter();
        MachineLearning.Chapter03.Program.Run(output);

        Assert.Equal(
            string.Join(
                Environment.NewLine,
                "深さ\t訓練データ\tテストデータ\tML.NET と一致",
                "1\t0.6952\t0.6000\t32/45",
                "2\t0.9619\t0.8889\t45/45",
                "3\t0.9714\t0.8889\t43/45",
                "4\t0.9810\t0.9111\t44/45",
                "5\t1.0000\t0.9111\t44/45",
                "制限なし\t1.0000\t0.9111\t44/45",
                string.Empty,
                "深さ 2 の決定木:",
                "花弁幅 <= 0.2750",
                "  Iris-setosa",
                "花弁幅 > 0.2750",
                "  花弁幅 <= 0.6900",
                "    Iris-versicolor",
                "  花弁幅 > 0.6900",
                "    Iris-virginica") + Environment.NewLine,
            output.ToString());
    }

    private TrainTestSplit<Features, string> IrisSplit() => Preprocessing.PrepareIris(this.csvFile, 0.3, 0);
}
