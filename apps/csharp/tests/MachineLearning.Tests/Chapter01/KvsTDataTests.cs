namespace MachineLearning.Tests.Chapter01;

using MachineLearning.Chapter01;
using MachineLearning.Dataset;

public class KvsTDataTests
{
    private readonly string csvFile = Path.Combine(DataDir.Current(), "KvsT.csv");

    [Fact(DisplayName = "実データから 19 人分を読み込む")]
    public void LoadsNineteenPeople()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ KvsT.csv が配置されていない（gulp data:setup）");

        Assert.Equal(19, KinokoTakenoko.LoadPeople(this.csvFile).Count);
    }

    [Fact(DisplayName = "ルールによる判定の正解率を実データで計算する")]
    public void AccuracyOfRule()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ KvsT.csv が配置されていない（gulp data:setup）");

        var (features, labels) = KinokoTakenoko.SplitFeaturesAndLabels(KinokoTakenoko.LoadPeople(this.csvFile));
        var predictions = features.Select(KinokoTakenoko.PredictByRule).ToList();

        Assert.Equal(14.0 / 19, KinokoTakenoko.Accuracy(predictions, labels), 12);
    }

    [Fact(DisplayName = "実行するとデータ件数と正解率を表示する")]
    public void PrintsSummary()
    {
        Assert.SkipUnless(File.Exists(this.csvFile), "学習データ KvsT.csv が配置されていない（gulp data:setup）");

        using var output = new StringWriter();
        Program.Run(output);

        Assert.Equal(
            "データ件数: 19" + Environment.NewLine + "ルールによる判定の正解率: 0.7368" + Environment.NewLine,
            output.ToString());
    }
}
